package com.example.codebasemcp.index;

import com.example.codebasemcp.index.Model.DataModel;
import com.example.codebasemcp.index.Model.DeclaredTopic;
import com.example.codebasemcp.index.Model.Endpoint;
import com.example.codebasemcp.index.Model.Field;
import com.example.codebasemcp.index.Model.KafkaConsumer;
import com.example.codebasemcp.index.Model.KafkaProducer;
import com.example.codebasemcp.index.Model.KafkaTopology;
import com.example.codebasemcp.index.Model.Overview;
import com.example.codebasemcp.index.Model.PackageSummary;
import com.example.codebasemcp.index.Model.TypeSummary;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Extracts Spring-specific structure from parsed sources. Everything is syntactic (no classpath),
 * so it works on any checkout without building it. Unresolvable values are returned as source text.
 */
@Component
public class CodebaseAnalyzer {

    private static final Map<String, String> MAPPING_ANNOTATIONS = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "PatchMapping", "PATCH",
            "DeleteMapping", "DELETE",
            "RequestMapping", "ANY");

    private static final Set<String> STEREOTYPES = Set.of(
            "RestController", "Controller", "Service", "Repository", "Component", "Configuration",
            "SpringBootApplication", "RestControllerAdvice", "ControllerAdvice", "Document", "Entity",
            "DynamoDbBean", "ConfigurationProperties");

    private final CodebaseIndex index;

    public CodebaseAnalyzer(CodebaseIndex index) {
        this.index = index;
    }

    // ---------------------------------------------------------------- REST

    public List<Endpoint> endpoints(String pathFilter) {
        List<Endpoint> result = new ArrayList<>();
        for (ParsedSource src : index.sources()) {
            ConfigProperties config = index.config(src.module());
            for (ClassOrInterfaceDeclaration type : src.unit().findAll(ClassOrInterfaceDeclaration.class)) {
                if (!Ast.hasAnyAnnotation(type, Set.of("RestController", "Controller"))) {
                    continue;
                }
                List<String> basePaths = Ast.annotation(type, "RequestMapping")
                        .flatMap(a -> Ast.attribute(a, "value", "path"))
                        .map(Ast::strings)
                        .orElse(List.of(""));
                for (MethodDeclaration method : type.getMethods()) {
                    for (AnnotationExpr a : method.getAnnotations()) {
                        String verb = MAPPING_ANNOTATIONS.get(Ast.simpleName(a));
                        if (verb == null) {
                            continue;
                        }
                        if (verb.equals("ANY")) {
                            verb = Ast.attribute(a, "method")
                                    .map(Ast::strings)
                                    .map(l -> String.join(",", l.stream().map(s -> s.replace("RequestMethod.", "")).toList()))
                                    .orElse("ANY");
                        }
                        List<String> subPaths = Ast.attribute(a, "value", "path").map(Ast::strings).orElse(List.of(""));
                        for (String base : basePaths) {
                            for (String sub : subPaths) {
                                String path = config.resolve(join(base, sub));
                                if (pathFilter != null && !pathFilter.isBlank() && !path.contains(pathFilter)) {
                                    continue;
                                }
                                result.add(new Endpoint(
                                        verb,
                                        path,
                                        type.getNameAsString() + "#" + method.getNameAsString(),
                                        paramsWith(method, "RequestBody").stream().findFirst().orElse(null),
                                        paramsWith(method, "PathVariable"),
                                        paramsWith(method, "RequestParam"),
                                        method.getType().asString(),
                                        new SourceLocation(src.relativePath(), Ast.line(method)).toString()));
                            }
                        }
                    }
                }
            }
        }
        result.sort(Comparator.comparing(Endpoint::path).thenComparing(Endpoint::httpMethod));
        return result;
    }

    private static List<String> paramsWith(MethodDeclaration method, String annotation) {
        return method.getParameters().stream()
                .filter(p -> Ast.annotation(p, annotation).isPresent())
                .map(p -> p.getType().asString() + " " + p.getNameAsString())
                .toList();
    }

    private static String join(String base, String sub) {
        String joined = (base + "/" + sub).replaceAll("/+", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            joined = joined.substring(0, joined.length() - 1);
        }
        return joined.startsWith("/") ? joined : "/" + joined;
    }

    // ---------------------------------------------------------------- Kafka

    public KafkaTopology kafka() {
        List<KafkaConsumer> consumers = new ArrayList<>();
        List<KafkaProducer> producers = new ArrayList<>();
        List<DeclaredTopic> declared = new ArrayList<>();
        Map<String, String> topicConfig = new TreeMap<>();

        for (ParsedSource src : index.sources()) {
            ConfigProperties config = index.config(src.module());
            config.all().forEach((k, v) -> {
                if (k.toLowerCase().contains("topic")) {
                    topicConfig.put(k, config.resolve(v));
                }
            });

            for (MethodDeclaration method : src.unit().findAll(MethodDeclaration.class)) {
                Optional<AnnotationExpr> listener = Ast.annotation(method, "KafkaListener");
                if (listener.isEmpty()) {
                    continue;
                }
                List<String> topics = Ast.attribute(listener.get(), "topics", "value")
                        .map(Ast::strings)
                        .orElse(List.of())
                        .stream()
                        .map(config::resolve)
                        .toList();
                String groupId = Ast.attribute(listener.get(), "groupId")
                        .map(e -> config.resolve(Ast.strings(e).getFirst()))
                        .orElseGet(() -> config.all().get("spring.kafka.consumer.group-id"));
                String payload = method.getParameters().stream()
                        .findFirst()
                        .map(p -> p.getType().asString())
                        .orElse(null);
                consumers.add(new KafkaConsumer(topics, groupId, owner(method) + "#" + method.getNameAsString(), payload,
                        new SourceLocation(src.relativePath(), Ast.line(method)).toString()));
            }

            for (ClassOrInterfaceDeclaration type : src.unit().findAll(ClassOrInterfaceDeclaration.class)) {
                Set<String> templateFields = new TreeSet<>();
                for (FieldDeclaration f : type.getFields()) {
                    if (f.getElementType().asString().startsWith("KafkaTemplate")) {
                        f.getVariables().forEach(v -> templateFields.add(v.getNameAsString()));
                    }
                }
                for (MethodCallExpr call : type.findAll(MethodCallExpr.class)) {
                    if (!templateFields.isEmpty() && call.getNameAsString().equals("send")
                            && call.getScope().map(s -> templateFields.contains(lastName(s))).orElse(false)
                            && call.getArguments().isNonEmpty()) {
                        Expression topicArg = call.getArgument(0);
                        producers.add(new KafkaProducer(
                                topicArg.toString(),
                                resolveTopic(topicArg, type, config),
                                type.getNameAsString() + "#" + enclosingMethod(call),
                                new SourceLocation(src.relativePath(), Ast.line(call)).toString()));
                    }
                    if (call.getNameAsString().equals("name")
                            && call.getScope().map(s -> s.toString().equals("TopicBuilder")).orElse(false)
                            && call.getArguments().isNonEmpty()) {
                        Expression topicArg = call.getArgument(0);
                        declared.add(new DeclaredTopic(
                                topicArg.toString(),
                                resolveTopic(topicArg, type, config),
                                new SourceLocation(src.relativePath(), Ast.line(call)).toString()));
                    }
                }
            }
        }
        return new KafkaTopology(consumers, producers, declared, topicConfig);
    }

    /**
     * Best-effort topic resolution: string literals, fields assigned once in the class, and
     * {@code @ConfigurationProperties} accessor chains like {@code props.topics().orderEvents()},
     * which are matched against config keys ending in {@code topics.order-events}.
     */
    private String resolveTopic(Expression expr, TypeDeclaration<?> owner, ConfigProperties config) {
        if (expr instanceof StringLiteralExpr s) {
            return config.resolve(s.getValue());
        }
        if (expr instanceof NameExpr || expr instanceof FieldAccessExpr) {
            String name = lastName(expr);
            for (AssignExpr assign : owner.findAll(AssignExpr.class)) {
                if (lastName(assign.getTarget()).equals(name)) {
                    return resolveTopic(assign.getValue(), owner, config);
                }
            }
            for (FieldDeclaration f : owner.getFields()) {
                for (var v : f.getVariables()) {
                    if (v.getNameAsString().equals(name) && v.getInitializer().isPresent()) {
                        return resolveTopic(v.getInitializer().get(), owner, config);
                    }
                }
            }
            return null;
        }
        if (expr instanceof MethodCallExpr call) {
            List<String> chain = new ArrayList<>();
            Expression current = call;
            while (current instanceof MethodCallExpr m && m.getArguments().isEmpty()) {
                chain.addFirst(Ast.kebab(m.getNameAsString()));
                current = m.getScope().orElse(null);
            }
            if (!chain.isEmpty()) {
                String suffix = String.join(".", chain);
                return config.all().entrySet().stream()
                        .filter(e -> e.getKey().endsWith(suffix))
                        .map(e -> config.resolve(e.getValue()))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    private static String lastName(Expression e) {
        if (e instanceof NameExpr n) {
            return n.getNameAsString();
        }
        if (e instanceof FieldAccessExpr f) {
            return f.getNameAsString();
        }
        return e.toString();
    }

    private static String owner(Node node) {
        return node.findAncestor(TypeDeclaration.class).map(t -> t.getNameAsString()).orElse("?");
    }

    private static String enclosingMethod(Node node) {
        return node.findAncestor(MethodDeclaration.class).map(MethodDeclaration::getNameAsString).orElse("<init>");
    }

    // ---------------------------------------------------------------- Data models

    public List<DataModel> dataModels(boolean includeRecords) {
        List<ParsedSource> sources = index.sources();
        Map<String, String> dynamoTables = dynamoTableNames(sources);
        List<DataModel> result = new ArrayList<>();
        for (ParsedSource src : sources) {
            for (TypeDeclaration<?> type : src.unit().findAll(TypeDeclaration.class)) {
                String location = new SourceLocation(src.relativePath(), Ast.line(type)).toString();
                Optional<AnnotationExpr> document = Ast.annotation(type, "Document");
                Optional<AnnotationExpr> entity = Ast.annotation(type, "Entity");
                if (document.isPresent()) {
                    String collection = Ast.attribute(document.get(), "value", "collection")
                            .map(e -> Ast.strings(e).getFirst())
                            .orElse(decapitalize(type.getNameAsString()));
                    result.add(new DataModel("mongodb", Ast.qualifiedName(type), collection,
                            fieldsAnnotated(type, Set.of("Id", "MongoId")), fields(type), location));
                } else if (entity.isPresent()) {
                    String table = Ast.annotation(type, "Table")
                            .flatMap(a -> Ast.attribute(a, "name"))
                            .map(e -> Ast.strings(e).getFirst())
                            .orElse(type.getNameAsString());
                    result.add(new DataModel("jpa", Ast.qualifiedName(type), table,
                            fieldsAnnotated(type, Set.of("Id", "EmbeddedId")), fields(type), location));
                } else if (Ast.annotation(type, "DynamoDbBean").isPresent()) {
                    result.add(new DataModel("dynamodb", Ast.qualifiedName(type),
                            dynamoTables.getOrDefault(type.getNameAsString(), "(table name set at runtime)"),
                            dynamoKeys(type), fields(type), location));
                } else if (includeRecords && type instanceof RecordDeclaration) {
                    result.add(new DataModel("record", Ast.qualifiedName(type), null, List.of(), fields(type), location));
                }
            }
        }
        return result;
    }

    /** Finds {@code client.table(<name>, TableSchema.fromBean(X.class))} and resolves the name. */
    private Map<String, String> dynamoTableNames(List<ParsedSource> sources) {
        Map<String, String> tables = new LinkedHashMap<>();
        for (ParsedSource src : sources) {
            ConfigProperties config = index.config(src.module());
            for (MethodCallExpr call : src.unit().findAll(MethodCallExpr.class)) {
                if (!call.getNameAsString().equals("table") || call.getArguments().size() != 2) {
                    continue;
                }
                call.getArgument(1).findFirst(ClassExpr.class).ifPresent(cls -> {
                    Expression nameArg = call.getArgument(0);
                    TypeDeclaration<?> owner = call.findAncestor(TypeDeclaration.class).orElse(null);
                    String resolved = owner == null ? null : resolveTopic(nameArg, owner, config);
                    tables.put(cls.getType().asString(), resolved != null ? resolved : nameArg.toString());
                });
            }
        }
        return tables;
    }

    private static List<String> dynamoKeys(TypeDeclaration<?> type) {
        List<String> keys = new ArrayList<>();
        for (MethodDeclaration m : type.getMethods()) {
            String property = decapitalize(m.getNameAsString().replaceFirst("^(get|is)", ""));
            if (Ast.annotation(m, "DynamoDbPartitionKey").isPresent()) {
                keys.add(property + " (partition key)");
            }
            if (Ast.annotation(m, "DynamoDbSortKey").isPresent()) {
                keys.add(property + " (sort key)");
            }
        }
        return keys;
    }

    private static List<String> fieldsAnnotated(TypeDeclaration<?> type, Set<String> annotations) {
        return fields(type).stream()
                .filter(f -> f.annotations().stream().anyMatch(a -> annotations.contains(a.replaceFirst("^@", "").replaceFirst("\\(.*", ""))))
                .map(Field::name)
                .toList();
    }

    private static List<Field> fields(TypeDeclaration<?> type) {
        List<Field> fields = new ArrayList<>();
        if (type instanceof RecordDeclaration record) {
            for (Parameter p : record.getParameters()) {
                fields.add(new Field(p.getNameAsString(), p.getType().asString(),
                        p.getAnnotations().stream().map(Object::toString).toList()));
            }
        }
        for (FieldDeclaration f : type.getFields()) {
            if (f.isStatic()) {
                continue;
            }
            for (var v : f.getVariables()) {
                fields.add(new Field(v.getNameAsString(), v.getType().asString(),
                        f.getAnnotations().stream().map(Object::toString).toList()));
            }
        }
        return fields;
    }

    private static String decapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------------------------------------------------------- Types

    public List<TypeSummary> describeType(String name) {
        List<ParsedSource> sources = index.sources();
        List<TypeSummary> result = new ArrayList<>();
        for (ParsedSource src : sources) {
            for (TypeDeclaration<?> type : src.unit().findAll(TypeDeclaration.class)) {
                String qualified = Ast.qualifiedName(type);
                if (!type.getNameAsString().equals(name) && !qualified.equals(name)) {
                    continue;
                }
                List<String> supertypes = new ArrayList<>();
                if (type instanceof ClassOrInterfaceDeclaration c) {
                    c.getExtendedTypes().forEach(t -> supertypes.add(t.asString()));
                    c.getImplementedTypes().forEach(t -> supertypes.add(t.asString()));
                } else if (type instanceof RecordDeclaration r) {
                    r.getImplementedTypes().forEach(t -> supertypes.add(t.asString()));
                }
                List<String> methods = type.getMethods().stream()
                        .filter(m -> !m.hasModifier(Modifier.Keyword.PRIVATE))
                        .map(m -> m.getDeclarationAsString(true, false, true))
                        .toList();
                result.add(new TypeSummary(
                        qualified,
                        kind(type),
                        type.getAnnotations().stream().map(Object::toString).toList(),
                        supertypes,
                        fields(type),
                        methods,
                        usages(sources, type.getNameAsString(), src),
                        new SourceLocation(src.relativePath(), Ast.line(type)).toString()));
            }
        }
        return result;
    }

    private static List<String> usages(List<ParsedSource> sources, String simpleName, ParsedSource self) {
        Set<String> users = new TreeSet<>();
        for (ParsedSource src : sources) {
            if (src == self) {
                continue;
            }
            boolean uses = src.unit().findAll(ClassOrInterfaceType.class).stream()
                    .anyMatch(t -> t.getNameAsString().equals(simpleName));
            if (uses) {
                src.unit().getPrimaryType().ifPresent(t -> users.add(Ast.qualifiedName(t)));
            }
        }
        return List.copyOf(users);
    }

    private static String kind(TypeDeclaration<?> type) {
        if (type instanceof RecordDeclaration) {
            return "record";
        }
        if (type.isEnumDeclaration()) {
            return "enum";
        }
        if (type.isAnnotationDeclaration()) {
            return "annotation";
        }
        if (type instanceof ClassOrInterfaceDeclaration c && c.isInterface()) {
            return "interface";
        }
        return "class";
    }

    // ---------------------------------------------------------------- Overview

    public Overview overview() {
        List<ParsedSource> sources = index.sources();
        Map<String, Map<String, Integer>> stereotypes = new TreeMap<>();
        Map<String, Integer> typeCounts = new TreeMap<>();
        Set<String> modules = new TreeSet<>();
        List<String> apps = new ArrayList<>();
        for (ParsedSource src : sources) {
            modules.add(src.module().isEmpty() ? "." : src.module());
            String pkg = src.unit().getPackageDeclaration().map(p -> p.getNameAsString()).orElse("(default)");
            for (TypeDeclaration<?> type : src.unit().getTypes()) {
                typeCounts.merge(pkg, 1, Integer::sum);
                Map<String, Integer> counts = stereotypes.computeIfAbsent(pkg, k -> new TreeMap<>());
                for (AnnotationExpr a : type.getAnnotations()) {
                    String n = Ast.simpleName(a);
                    if (STEREOTYPES.contains(n)) {
                        counts.merge(n, 1, Integer::sum);
                    }
                    if (n.equals("SpringBootApplication")) {
                        apps.add(Ast.qualifiedName(type));
                    }
                }
                if (type instanceof ClassOrInterfaceDeclaration c && c.isInterface()
                        && c.getExtendedTypes().stream().anyMatch(t -> t.getNameAsString().endsWith("Repository"))) {
                    counts.merge("Repository", 1, Integer::sum);
                }
            }
        }
        KafkaTopology kafka = kafka();
        List<PackageSummary> packages = typeCounts.entrySet().stream()
                .map(e -> new PackageSummary(e.getKey(), e.getValue(), stereotypes.getOrDefault(e.getKey(), Map.of())))
                .toList();
        return new Overview(index.root().toString(), List.copyOf(modules), apps, sources.size(),
                endpoints(null).size(), kafka.consumers().size(), kafka.producers().size(), packages);
    }
}
