package dev.njk.resolve;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import dev.njk.NjkFile;
import dev.njk.index.NjkIndex;
import dev.njk.index.NjkSourceScanner;
import dev.njk.psi.NjkStringLiteral;
import dev.njk.psi.NjkTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Queries over {@link NjkIndex}. */
public final class NjkLookup {
    private NjkLookup() {
    }

    public static @NotNull Collection<VirtualFile> files(@NotNull Project project, @NotNull String key) {
        if (DumbService.isDumb(project)) return List.of();
        return FileBasedIndex.getInstance().getContainingFiles(NjkIndex.NAME, key, GlobalSearchScope.projectScope(project));
    }

    /** Names of all index keys with the given prefix that occur in project files. */
    public static @NotNull Set<String> names(@NotNull Project project, @NotNull String prefix) {
        Set<String> result = new TreeSet<>();
        if (DumbService.isDumb(project)) return result;
        for (String key : FileBasedIndex.getInstance().getAllKeys(NjkIndex.NAME, project)) {
            if (key.startsWith(prefix) && !files(project, key).isEmpty()) result.add(key.substring(prefix.length()));
        }
        return result;
    }

    /** Exact places in project files where the given key occurs. */
    public static @NotNull List<NjkSourceTarget> targets(@NotNull Project project, @NotNull String key,
                                                         @NotNull String description) {
        List<NjkSourceTarget> result = new ArrayList<>();
        for (VirtualFile file : files(project, key)) {
            PsiFile psi = PsiManager.getInstance(project).findFile(file);
            if (psi == null) continue;
            for (NjkSourceScanner.Entry entry : NjkSourceScanner.scan(psi.getViewProvider().getContents(), file.getExtension())) {
                if (entry.key().equals(key)) {
                    result.add(new NjkSourceTarget(psi, entry.offset(), entry.name(), description + " in " + file.getName()));
                }
            }
        }
        return result;
    }

    /** extends/include/import/from tags in other templates that point at {@code target}. */
    public static @NotNull List<NjkTag> referencingTags(@NotNull NjkFile target) {
        return CachedValuesManager.getCachedValue(target, () ->
                CachedValueProvider.Result.create(computeReferencingTags(target),
                        PsiModificationTracker.getInstance(target.getProject())));
    }

    private static List<NjkTag> computeReferencingTags(NjkFile target) {
        VirtualFile vf = target.getOriginalFile().getVirtualFile();
        if (vf == null) return List.of();
        Project project = target.getProject();
        List<NjkTag> result = new ArrayList<>();
        for (VirtualFile file : files(project, NjkSourceScanner.TEMPLATE + NjkSourceScanner.baseName(vf.getName()))) {
            if (file.getExtension() == null || !NjkSourceScanner.TEMPLATE_EXTENSIONS.contains(file.getExtension())) continue;
            NjkFile njk = NjkTemplatePaths.njkFile(project, file);
            if (njk == null) continue;
            for (NjkTag tag : PsiTreeUtil.findChildrenOfType(njk, NjkTag.class)) {
                for (NjkStringLiteral path : tag.getTemplatePaths()) {
                    if (NjkTemplatePaths.refersTo(path.getValue(), vf, project, file)) {
                        result.add(tag);
                        break;
                    }
                }
            }
        }
        return result;
    }

    /** Templates the given tag points at. */
    public static @NotNull List<NjkFile> targetsOf(@NotNull NjkTag tag) {
        List<NjkFile> result = new ArrayList<>();
        for (NjkStringLiteral path : tag.getTemplatePaths()) {
            for (NjkFile file : NjkTemplatePaths.resolveToNjkFiles(path.getValue(), tag.getContainingFile())) {
                if (!result.contains(file)) result.add(file);
            }
        }
        return result;
    }

    /** Templates this file extends, nearest first, following the whole chain. */
    public static @NotNull List<NjkFile> parentTemplates(@NotNull NjkFile file) {
        List<NjkFile> result = new ArrayList<>();
        Deque<NjkFile> queue = new ArrayDeque<>(List.of(file));
        while (!queue.isEmpty() && result.size() < 10) {
            NjkFile current = queue.poll();
            NjkTag extendsTag = extendsTag(current);
            if (extendsTag == null) continue;
            for (NjkFile parent : targetsOf(extendsTag)) {
                if (parent != file && !result.contains(parent)) {
                    result.add(parent);
                    queue.add(parent);
                }
            }
        }
        return result;
    }

    public static @Nullable NjkTag extendsTag(@NotNull NjkFile file) {
        for (NjkTag tag : PsiTreeUtil.findChildrenOfType(file, NjkTag.class)) {
            if ("extends".equals(tag.getTagName())) return tag;
        }
        return null;
    }
}
