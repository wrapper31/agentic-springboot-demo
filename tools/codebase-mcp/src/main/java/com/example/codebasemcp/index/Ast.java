package com.example.codebasemcp.index;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Small helpers over JavaParser nodes. Annotation matching is by simple name, so imports need not resolve. */
final class Ast {

    private Ast() {}

    static Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream().filter(a -> simpleName(a).equals(simpleName)).findFirst();
    }

    static boolean hasAnyAnnotation(NodeWithAnnotations<?> node, Set<String> simpleNames) {
        return node.getAnnotations().stream().anyMatch(a -> simpleNames.contains(simpleName(a)));
    }

    static String simpleName(AnnotationExpr a) {
        return a.getName().getIdentifier();
    }

    /** Value of an annotation attribute; {@code "value"} also matches the single-member form. */
    static Optional<Expression> attribute(AnnotationExpr a, String... names) {
        if (a instanceof SingleMemberAnnotationExpr single) {
            for (String n : names) {
                if (n.equals("value")) {
                    return Optional.of(single.getMemberValue());
                }
            }
            return Optional.empty();
        }
        if (a instanceof NormalAnnotationExpr normal) {
            for (String n : names) {
                for (MemberValuePair pair : normal.getPairs()) {
                    if (pair.getNameAsString().equals(n)) {
                        return Optional.of(pair.getValue());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** String values of a literal or array-of-literals; other expressions are returned as source text. */
    static List<String> strings(Expression e) {
        if (e instanceof ArrayInitializerExpr array) {
            return array.getValues().stream().flatMap(v -> strings(v).stream()).toList();
        }
        if (e instanceof StringLiteralExpr s) {
            return List.of(s.getValue());
        }
        return List.of(e.toString());
    }

    static int line(Node node) {
        return node.getBegin().map(p -> p.line).orElse(0);
    }

    static String qualifiedName(TypeDeclaration<?> type) {
        return type.getFullyQualifiedName().orElse(type.getNameAsString());
    }

    static String kebab(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
