package hm.binkley.annotation.processing;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@code LoadedYaml} <b>needs documentation</b>.
 *
 * @author <a href="mailto:binkley@alumni.rice.edu">B. K. Oxley (binkley)</a>
 * @todo Needs documentation.
 */
public final class LoadedYaml
        extends Loaded<Map<String, Map<String, Map<String, Object>>>> {
    public final String path;

    LoadedYaml(final String pathPattern, final Resource whence,
            final Map<String, Map<String, Map<String, Object>>> yaml,
            final List<String> roots)
            throws IOException {
        super(pathPattern, whence, yaml);
        path = path(pathPattern, whence, roots);
    }

    @Override
    public String where() {
        return path;
    }
}
