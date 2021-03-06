/*
 * This is free and unencumbered software released into the public domain.
 *
 * Please see https://github.com/binkley/binkley/blob/master/LICENSE.md.
 */

package hm.binkley.util;

import org.junit.Test;

import java.io.IOError;
import java.io.IOException;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static hm.binkley.util.Mixin.newMixin;
import static hm.binkley.util.MixinTest.DefaultMethodValue.defaultValue;
import static hm.binkley.util.MixinTest.Duck.QUACKERS;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@code MixinTest} tests {@link Mixin}.
 *
 * @author <a href="mailto:binkley@alumni.rice.edu">B. K. Oxley (binkley)</a>
 */
public class MixinTest {
    @Test
    public void shouldProxyToString() {
        assertThat(newMixin(Testy.class, new Object() {
            @Override
            public String toString() {
                return "bob";
            }
        }).toString(), is(equalToIgnoringCase("bob")));
    }

    @Test
    public void shouldStaticType()
            throws IOException {
        final int roll = 13;
        assertThat(newMixin(Testy.class, (Bob) ignored -> roll)
                .throwDown("Hoe down!"), is(equalTo(roll)));
    }

    @Test
    public void shouldPickFirstMatch()
            throws IOException {
        final int firstRoll = 14;
        assertThat(newMixin(Testy.class, (Bob) ignored -> firstRoll,
                (Bob) ignored -> firstRoll - 1).throwDown("Hoe down!"),
                is(equalTo(firstRoll)));
    }

    @Test
    public void shouldDuckType() {
        assertThat(newMixin(Testy.class, new Duck()).quack(3),
                is(equalTo(QUACKERS)));
    }

    @Test(expected = IOException.class)
    public void shouldPassThroughCheckedExceptionsFromDuckTyping()
            throws IOException {
        newMixin(Testy.class, new Die()).die();
    }

    @Test(expected = IOError.class)
    public void shouldPassThroughUncheckedExceptionsFromDuckTyping()
            throws IOException {
        newMixin(Testy.class, new DieHarder()).die();
    }

    @Test(expected = IOException.class)
    public void shouldPassThroughCheckedExceptionsFromStaticTyping()
            throws IOException {
        newMixin(Testy.class, (Bob) ignored -> {
            throw new IOException("The horror!");
        }).throwDown("not used");
    }

    @Test(expected = IOError.class)
    public void shouldPassThroughUncheckedExceptionsFromStaticTyping()
            throws IOException {
        newMixin(Testy.class, (Bob) ignored -> {
            throw new IOError(new IOException("The horror!"));
        }).throwDown("not used");
    }

    @Test
    public void shouldPassThroughClassAnnotationsOnDelegates()
            throws NoSuchMethodException {
        for (final Object object : newMixin(Testy.class, new UnBob())
                .mixinDelegates())
            if (null != object.getClass().getAnnotation(Beans.class))
                return;
        fail("No @Beans on Bob proxy");
    }

    @Test
    public void shouldPassThroughMethodAnnotationsOnInterfaces()
            throws NoSuchMethodException {
        for (final Class<?> itf : newMixin(Testy.class, new UnBob())
                .getClass().getInterfaces())
            if (null != itf.getMethod("throwDown", String.class)
                    .getAnnotation(Cool.class))
                return;
        fail("No @Cool on Bob.throwDown(String) proxy");
    }

    @Test(expected = AbstractMethodError.class)
    public void shouldThrowIfUnimplemented()
            throws IOException {
        newMixin(Testy.class, new Object()).die();
    }

    @Test
    public void shouldFindDefaultMethod() {
        newMixin(DefaultMethodPublic.class).foo();
    }

    @Test
    public void shouldFindDefaultMethodsOnTwoInterfaces() {
        newMixin(DescendantWithDefaultMethod.class).bar();
    }

    @Test
    public void shouldUseDefaultValue() {
        assertThat(newMixin(DefaultMethodValue.class).foo(),
                is(equalTo(defaultValue)));
    }

    @Test
    public void shouldOverrideDefaultValue() {
        assertThat(newMixin(DefaultMethodValue.class, new Object() {
                    public int foo() {
                        return 6;
                    }
                }).foo(), is(equalTo(6)));
    }

    @Test(expected = IllegalAccessError.class)
    public void shouldThrowOnNonPublicForDefaultMethod() {
        newMixin(DefaultMethodNotPublic.class).foo();
    }

    @Test
    public void shouldWorkWhenTStaticMethodsPresent() {
        newMixin(WithStaticMethod.class);
    }

    @Test
    public void shouldWorkAgainstInternalStaticsFromAnotherPackage()
            throws ExecutionException, InterruptedException {
        final ExecutorService threads = newMixin(ExecutorService.class,
                newSingleThreadExecutor());
        // Check twice - once lookup, second cached
        assertThat(threads.submit(() -> 3).get(), is(equalTo(3)));
        assertThat(threads.submit(() -> 3).get(), is(equalTo(3)));
    }

    public interface DefaultMethodPublic {
        default void foo() {
        }
    }

    interface DefaultMethodNotPublic {
        default void foo() {
        }
    }

    public interface DefaultMethodOther {
        default void bar() {
        }
    }

    public interface DefaultMethodValue {
        int defaultValue = 3;

        default int foo() {
            return defaultValue;
        }
    }

    public interface DescendantWithDefaultMethod
            extends DefaultMethodPublic, DefaultMethodOther {}

    interface Testy
            extends Bob, Mixin {
        String quack(final int quacks);

        void die()
                throws IOException;
    }

    @Retention(RUNTIME)
    @Target(METHOD)
    @interface Cool {}

    @Retention(RUNTIME)
    @Target(TYPE)
    @Inherited
    @interface Beans {}

    interface Bob {
        @Cool
        int throwDown(final String ignored)
                throws IOException;
    }

    static class Duck {
        static final String QUACKERS = "Quack! Quack! Bob!";

        @SuppressWarnings("UnusedDeclaration")
        public String quack(final int ignored) {
            return QUACKERS;
        }
    }

    static class Die {
        @SuppressWarnings("UnusedDeclaration")
        public void die()
                throws IOException {
            throw new IOException("Oh noes!");
        }
    }

    static class DieHarder {
        @SuppressWarnings("UnusedDeclaration")
        public void die() {
            throw new IOError(new IOException("Oh noes!"));
        }
    }

    @Beans
    private static class PreBob {}

    private static final class UnBob
            extends PreBob
            implements Bob {
        @Override
        public int throwDown(final String ignored) {
            return 0;
        }
    }

    public interface WithStaticMethod {
        static void staticMethod() {
        }
    }
}
