package dev.njk.resolve;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Detects Eleventy projects, which add their own filters and global data to Nunjucks. */
public final class NjkEleventy {
    private static final List<String> CONFIG_FILES = List.of(
            ".eleventy.js", ".eleventy.cjs", ".eleventy.mjs",
            "eleventy.config.js", "eleventy.config.cjs", "eleventy.config.mjs", "eleventy.config.ts");

    private NjkEleventy() {
    }

    public static boolean isEleventyProject(@NotNull Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () ->
                CachedValueProvider.Result.create(compute(project), PsiModificationTracker.getInstance(project)));
    }

    private static boolean compute(Project project) {
        if (DumbService.isDumb(project)) return false;
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (String name : CONFIG_FILES) {
            if (!FilenameIndex.getVirtualFilesByName(name, scope).isEmpty()) return true;
        }
        return false;
    }
}
