package dev.njk.resolve;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal JavaScript scanner that extracts the property names of an object literal passed as the second
 * argument of a call, e.g. {@code title} and {@code user} in {@code res.render("page", { title: "x", user })}.
 */
public final class NjkJsObjects {
    public record Key(@NotNull String name, int offset) {
    }

    private NjkJsObjects() {
    }

    /** Keys of the object literal following {@code , } at {@code from} (just after the first call argument). */
    public static @NotNull List<Key> keysOfSecondArgument(@NotNull CharSequence text, int from) {
        int i = skipTrivia(text, from);
        if (i >= text.length() || text.charAt(i) != ',') return List.of();
        i = skipTrivia(text, i + 1);
        if (i >= text.length() || text.charAt(i) != '{') return List.of();
        return objectKeys(text, i);
    }

    /** Property names at the top level of the object literal starting at {@code open}. */
    public static @NotNull List<Key> objectKeys(@NotNull CharSequence text, int open) {
        List<Key> keys = new ArrayList<>();
        int depth = 0;
        boolean expectKey = false;
        int i = open;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < n && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*')) {
                i = skipTrivia(text, i);
                continue;
            }
            if (depth == 1 && expectKey && !Character.isWhitespace(c)) {
                expectKey = false;
                if (isIdentStart(c)) {
                    int end = i + 1;
                    while (end < n && isIdentPart(text.charAt(end))) end++;
                    int next = skipTrivia(text, end);
                    char nc = next < n ? text.charAt(next) : '\0';
                    if (nc == ':' || nc == '(' || nc == ',' || nc == '}') {
                        keys.add(new Key(text.subSequence(i, end).toString(), i));
                    }
                    i = end;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    int end = skipString(text, i);
                    int next = skipTrivia(text, end);
                    if (next < n && text.charAt(next) == ':' && end - 1 > i + 1) {
                        keys.add(new Key(text.subSequence(i + 1, end - 1).toString(), i + 1));
                    }
                    i = end;
                    continue;
                }
            }
            if (c == '"' || c == '\'' || c == '`') {
                i = skipString(text, i);
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
                if (depth == 1) expectKey = true;
            } else if (c == '}' || c == ']' || c == ')') {
                depth--;
                if (depth <= 0) break;
            } else if (c == ',' && depth == 1) {
                expectKey = true;
            }
            i++;
        }
        return keys;
    }

    private static int skipString(CharSequence text, int start) {
        char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            i++;
            if (c == quote) break;
        }
        return Math.min(i, text.length());
    }

    private static int skipTrivia(CharSequence text, int start) {
        int i = start;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = text.toString().indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else {
                break;
            }
        }
        return i;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
