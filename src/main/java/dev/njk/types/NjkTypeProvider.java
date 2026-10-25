package dev.njk.types;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import dev.njk.resolve.NjkSourceTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Connects templates to the code around them. The plugin ships a JavaScript/TypeScript implementation that is
 * loaded only when the JavaScript plugin is installed.
 */
public interface NjkTypeProvider {
    ExtensionPointName<NjkTypeProvider> EP_NAME = ExtensionPointName.create("dev.njk.typeProvider");

    /** A variable passed into a template from code. */
    record ContextVariable(@NotNull String name, @NotNull PsiElement target, @NotNull String description) {
    }

    /** What a filter (or other function) registered in code accepts and returns. */
    record FunctionInfo(@NotNull String parameters, @Nullable NjkType returnType, @NotNull String returnTypeText) {
    }

    /** Type of something a template name resolved to outside of templates: a TS property, an addGlobal() call, ... */
    @Nullable NjkType typeOf(@NotNull PsiElement target);

    /**
     * Variables passed by the render call whose template path literal starts at {@code pathOffset} in {@code script},
     * or null if this provider can't handle the file (the plugin then falls back to scanning the text).
     */
    @Nullable List<ContextVariable> renderContext(@NotNull PsiFile script, int pathOffset);

    /** Signature of the function registered at {@code registration}, e.g. by {@code addFilter("name", fn)}. */
    @Nullable FunctionInfo functionInfo(@NotNull NjkSourceTarget registration);

    /** Evaluates the type in a {@code {# @type {User} user #}} annotation written in {@code context}. */
    @Nullable NjkType resolveTypeText(@NotNull String typeText, @NotNull PsiElement context);
}
