package com.example.codebasemcp.index;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * Flattened view of a module's application.yml / application.properties (default profile only),
 * used to resolve {@code ${...}} placeholders found in annotations.
 */
public final class ConfigProperties {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    private final Map<String, String> values;

    private ConfigProperties(Map<String, String> values) {
        this.values = values;
    }

    public static ConfigProperties load(Path resourcesDir) {
        Map<String, String> values = new LinkedHashMap<>();
        Path yml = resourcesDir.resolve("application.yml");
        Path yaml = resourcesDir.resolve("application.yaml");
        Path props = resourcesDir.resolve("application.properties");
        for (Path p : new Path[] {yml, yaml}) {
            if (Files.isRegularFile(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    for (Object doc : new Yaml().loadAll(in)) {
                        if (doc instanceof Map<?, ?> map) {
                            flatten("", map, values);
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    // Unreadable config is reported as unresolved placeholders rather than failing the tool.
                }
            }
        }
        if (Files.isRegularFile(props)) {
            try (InputStream in = Files.newInputStream(props)) {
                Properties p = new Properties();
                p.load(in);
                p.forEach((k, v) -> values.put(k.toString(), v.toString()));
            } catch (IOException e) {
                // see above
            }
        }
        return new ConfigProperties(values);
    }

    private static void flatten(String prefix, Map<?, ?> map, Map<String, String> out) {
        map.forEach((k, v) -> {
            String key = prefix.isEmpty() ? String.valueOf(k) : prefix + "." + k;
            if (v instanceof Map<?, ?> nested) {
                flatten(key, nested, out);
            } else {
                out.put(key, v == null ? "" : String.valueOf(v));
            }
        });
    }

    public Map<String, String> all() {
        return values;
    }

    /** Resolves {@code ${key}} / {@code ${key:default}} (one level of env-var style nesting); returns input if unresolvable. */
    public String resolve(String text) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value != null) {
                value = resolveDefaultsOnly(value);
            } else if (m.group(2) != null) {
                value = m.group(2);
            } else {
                value = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String resolveDefaultsOnly(String value) {
        Matcher m = PLACEHOLDER.matcher(value);
        if (m.matches() && m.group(2) != null) {
            return m.group(2);
        }
        return value;
    }
}
