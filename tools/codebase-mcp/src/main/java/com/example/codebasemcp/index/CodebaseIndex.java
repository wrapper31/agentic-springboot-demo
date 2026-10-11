package com.example.codebasemcp.index;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parses every {@code src/main/java} file under the codebase root (multi-module repos included)
 * and caches the ASTs by modification time, so repeated tool calls only re-parse changed files.
 */
@Component
public class CodebaseIndex {

    private static final String MAIN_JAVA = "src/main/java";

    private final Path root;
    private final JavaParser parser;
    private final Map<Path, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, ConfigProperties> configByModule = new ConcurrentHashMap<>();

    private record Cached(FileTime modified, ParsedSource source) {}

    public CodebaseIndex(@Value("${codebase.root:.}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    }

    public Path root() {
        return root;
    }

    public synchronized List<ParsedSource> sources() {
        List<Path> files = javaFiles();
        cache.keySet().retainAll(files);
        configByModule.clear();
        List<ParsedSource> result = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                FileTime modified = Files.getLastModifiedTime(file);
                Cached cached = cache.get(file);
                if (cached == null || !cached.modified().equals(modified)) {
                    ParseResult<CompilationUnit> parsed = parser.parse(file);
                    if (parsed.getResult().isEmpty()) {
                        continue;
                    }
                    cached = new Cached(modified, new ParsedSource(relative(file), moduleOf(file), parsed.getResult().get()));
                    cache.put(file, cached);
                }
                result.add(cached.source());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return result;
    }

    public ConfigProperties config(String module) {
        return configByModule.computeIfAbsent(module, m -> ConfigProperties.load(root.resolve(m).resolve("src/main/resources")));
    }

    private List<Path> javaFiles() {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/" + MAIN_JAVA + "/"))
                    .filter(p -> !p.toString().contains("/target/") && !p.toString().contains("/build/"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String relative(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private String moduleOf(Path file) {
        String rel = relative(file);
        int idx = rel.indexOf(MAIN_JAVA + "/");
        String module = idx <= 0 ? "" : rel.substring(0, idx - 1);
        return module;
    }
}
