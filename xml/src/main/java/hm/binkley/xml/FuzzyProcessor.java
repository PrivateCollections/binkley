/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>.
 */

package hm.binkley.xml;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import hm.binkley.xml.Fuzzy.Field;
import org.kohsuke.MetaInfServices;
import org.w3c.dom.Node;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Arrays.asList;
import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.StandardLocation.CLASS_PATH;

/**
 * {@code FuzzyProcessor} <b>needs documentation</b>.
 *
 * @author <a href="mailto:binkley@alumni.rice.edu">B. K. Oxley (binkley)</a>
 * @todo Needs documentation.
 * @todo Think through use of FreeMarker - logging dependencies, runtime dependency
 * @todo Support inner interfaces
 * @todo How to map evaluate parameters onto xpath resolver map?
 */
@MetaInfServices(Processor.class)
@SupportedAnnotationTypes({"hm.binkley.xml.Fuzzy", "hm.binkley.xml.Fuzzy.Field"})
@SupportedSourceVersion(RELEASE_8)
@NotThreadSafe
public class FuzzyProcessor
        extends AbstractProcessor {
    private static final XPath xpath = XPathFactory.newInstance().newXPath();
    private static final Map<String, XPathExpression> expressions = new HashMap<>();

    /**
     * Evaluate an XPath <var>expression</var> against <var>node</var> using a common, shared cache
     * for compiled expressions.
     *
     * @param node the document node, never missing
     * @param expression the xpath expression, never missing
     *
     * @return the evaluation result
     *
     * @throws XPathExpressionException
     * @todo Is the return nullable?
     * @todo Thread safety
     */
    public static String evaluate(@Nonnull final Node node, @Nonnull final String expression)
            throws XPathExpressionException {
        try {
            return expressions.computeIfAbsent(expression, expr -> {
                try {
                    return xpath.compile(expr);
                } catch (final XPathExpressionException e) {
                    throw new Passing(e);
                }
            }).evaluate(node).intern();
        } catch (final Passing passing) {
            throw (XPathExpressionException) passing.getCause();
        }
    }

    private static final class Passing
            extends RuntimeException {
        private Passing(final XPathExpressionException real) {
            super(real);
        }
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations,
            final RoundEnvironment roundEnv) {
        final Configuration configuration = new Configuration();
        try {
            configuration.setDirectoryForTemplateLoading(new File(processingEnv.getFiler()
                    .getResource(CLASS_PATH, getClass().getPackage().getName(), "fuzzy.ftl")
                    .toUri()).getParentFile());
        } catch (final IOException e) {
            processingEnv.getMessager().printMessage(ERROR, e.toString());
            return false;
        }

        for (final Element element : roundEnv.getElementsAnnotatedWith(Fuzzy.class)) {
            try {
                final PackageElement packaj = processingEnv.getElementUtils().getPackageOf(element);
                final Name simpleName = element.getSimpleName();
                try (final Writer out = processingEnv.getFiler()
                        .createSourceFile(packaj + "." + simpleName + "Factory", element)
                        .openWriter()) {
                    final Map<String, Object> model = new HashMap<>();
                    model.put("package", packaj);
                    model.put("simpleName", simpleName);
                    //noinspection unchecked
                    final Set<ExecutableElement> methodElements = (Set<ExecutableElement>) roundEnv
                            .getElementsAnnotatedWith(Field.class);
                    final List<Map<String, Object>> methodModels = new ArrayList<>(
                            methodElements.size());
                    methodElements.forEach(methodElement -> {
                        if (!methodElement.getEnclosingElement().equals(element))
                            return;
                        if (!methodElement.getParameters().isEmpty())
                            throw new UnsupportedOperationException(
                                    "Too many parameters on @Fuzzy.Field method: " + methodElement);
                        final Map<String, Object> methodModel = new HashMap<>();
                        methodModel.put("simpleName", methodElement.getSimpleName());
                        final TypeMirror returnType = methodElement.getReturnType();
                        methodModel.put("returnType", returnType);
                        methodModel.put("xpath", methodElement.getAnnotation(Field.class).value());
                        // TODO: Borrow from Converter to work out correct conversion method
                        methodModel.put("converter", converterFor(methodElement, returnType));

                        methodModels.add(methodModel);
                    });
                    model.put("methods", methodModels);

                    configuration.getTemplate("fuzzy.ftl").process(model, out);
                }
            } catch (final IOException | TemplateException e) {
                processingEnv.getMessager().printMessage(ERROR, e.toString(), element);
                return false;
            }
        }
        return true;
    }

    private String converterFor(final ExecutableElement methodElement, final TypeMirror returnType) {
        try {
            return processingEnv.getTypeUtils()
                    .boxedClass(processingEnv.getTypeUtils().getPrimitiveType(returnType.getKind()))
                    + ".valueOf";
        } catch (final IllegalArgumentException e) {
            // Not primitive
        }
        try {
            final Class<?> clazz = Class.forName(returnType.toString());
            for (final String methodName : asList("parse", "valueOf", "of"))
                for (final Class<?> parameterType : asList(String.class, CharSequence.class))
                    try {
                        if (isStatic(clazz.getMethod(methodName, parameterType).getModifiers()))
                            return returnType + "." + methodName;
                    } catch (final NoSuchMethodException ignored) {
                    }
            for (final Class<?> parameterType : asList(String.class, CharSequence.class))
                try {
                    clazz.getConstructor(parameterType);
                    return "new " + returnType;
                } catch (final NoSuchMethodException ignored) {
                }
        } catch (final ClassNotFoundException x) {
            processingEnv.getMessager().printMessage(ERROR, x.toString(), methodElement,
                    (AnnotationMirror) processingEnv.getElementUtils()
                            .getTypeElement(Field.class.getName()).asType());
        }
        processingEnv.getMessager()
                .printMessage(ERROR, format("No converter for '%s'", returnType), methodElement,
                        (AnnotationMirror) processingEnv.getElementUtils()
                                .getTypeElement(Field.class.getName()).asType());
        return null;
    }
}
