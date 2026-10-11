package com.example.codebasemcp.index;

import com.github.javaparser.ast.CompilationUnit;

/** One parsed Java file. {@code module} is the directory containing {@code src/main/java}, relative to the root. */
public record ParsedSource(String relativePath, String module, CompilationUnit unit) {}
