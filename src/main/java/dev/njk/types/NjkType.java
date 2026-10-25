package dev.njk.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The type of a value as far as a template is concerned: what can follow a dot, what a for loop yields
 * and what a call returns. Implemented for template constructs (dicts, lists, imported templates) and, when
 * the JavaScript plugin is present, for TypeScript/JavaScript types.
 */
public interface NjkType {
    /** Members reachable with {@code .name}, in declaration order. */
    @NotNull Map<String, NjkMember> members();

    /** Type of items produced by {@code {% for x in value %}} or {@code value[i]}. */
    default @Nullable NjkType elementType() {
        return null;
    }

    /** Type of {@code value(...)}. */
    default @Nullable NjkType returnType() {
        return null;
    }

    /** Short display text, e.g. {@code User[]}. */
    @NotNull String presentableText();
}
