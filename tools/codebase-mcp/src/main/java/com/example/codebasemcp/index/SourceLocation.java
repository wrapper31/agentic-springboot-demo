package com.example.codebasemcp.index;

/** A file path relative to the codebase root plus a 1-based line, rendered as {@code path:line}. */
public record SourceLocation(String file, int line) {

    @Override
    public String toString() {
        return file + ":" + line;
    }
}
