package dev.njk.resolve;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import dev.njk.NjkFile;
import dev.njk.NjkFileType;
import dev.njk.NjkLanguage;
import dev.njk.index.NjkSourceScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Resolves template paths as written in extends/include/import/from or render() calls.
 * <p>
 * Nunjucks resolves non-relative paths against the search paths passed to {@code configure()}, which the IDE
 * can't know. Instead, paths are matched as suffixes of project file paths. Template roots are inferred from
 * references that match exactly one file, and are used to pick the right file when a suffix is ambiguous.
 */
public final class NjkTemplatePaths {
    public static final List<String> DEFAULT_EXTENSIONS = List.of("njk", "nunjucks", "html");
    private static final int MAX_FILES_FOR_ROOTS = 400;

    private NjkTemplatePaths() {
    }

    public static boolean isRelative(@NotNull String path) {
        return path.startsWith("./") || path.startsWith("../");
    }

    private static boolean hasExtension(@NotNull String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.indexOf('.') > 0;
    }

    private static String normalize(String path) {
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    /** Templates the path may refer to when written in {@code from}, best match first. */
    public static @NotNull List<VirtualFile> resolve(@NotNull String path, @NotNull PsiFile from) {
        VirtualFile fromFile = from.getOriginalFile().getVirtualFile();
        return resolve(path, from.getProject(), fromFile);
    }

    public static @NotNull List<VirtualFile> resolve(@NotNull String rawPath, @NotNull Project project,
                                                     @Nullable VirtualFile fromFile) {
        if (rawPath.isBlank() || rawPath.contains("{{") || DumbService.isDumb(project)) return List.of();
        String path = rawPath.trim().replace('\\', '/');
        if (isRelative(path)) {
            VirtualFile dir = fromFile != null ? fromFile.getParent() : null;
            VirtualFile found = dir != null ? findRelative(project, dir, path) : null;
            return found != null ? List.of(found) : List.of();
        }
        // Search paths configured in code are what Nunjucks itself uses, so an exact match there wins.
        List<VirtualFile> configured = new ArrayList<>();
        for (VirtualFile root : configuredRoots(project)) {
            VirtualFile exact = findRelative(project, root, normalize(path));
            if (exact != null && !configured.contains(exact)) configured.add(exact);
        }
        if (!configured.isEmpty()) return configured;
        List<VirtualFile> candidates = suffixCandidates(project, normalize(path));
        if (candidates.size() > 1) {
            List<VirtualFile> underRoot = new ArrayList<>();
            for (VirtualFile root : templateRoots(project)) {
                VirtualFile exact = findRelative(project, root, normalize(path));
                if (exact != null && candidates.contains(exact) && !underRoot.contains(exact)) underRoot.add(exact);
            }
            if (!underRoot.isEmpty()) return underRoot;
            if (fromFile != null) {
                candidates.sort(Comparator.comparingInt((VirtualFile f) -> -commonPrefixLength(f.getPath(), fromFile.getPath())));
            }
        }
        return candidates;
    }

    private static @Nullable VirtualFile findRelative(Project project, VirtualFile dir, String path) {
        VirtualFile f = dir.findFileByRelativePath(path);
        if (f != null && !f.isDirectory()) return f;
        if (!hasExtension(path)) {
            for (String ext : extensions(project)) {
                f = dir.findFileByRelativePath(path + "." + ext);
                if (f != null && !f.isDirectory()) return f;
            }
        }
        return null;
    }

    private static List<VirtualFile> suffixCandidates(Project project, String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (fileName.isEmpty()) return new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (hasExtension(fileName)) {
            names.add(fileName);
        } else {
            for (String ext : extensions(project)) names.add(fileName + "." + ext);
        }
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        List<VirtualFile> result = new ArrayList<>();
        for (String name : names) {
            for (VirtualFile file : FilenameIndex.getVirtualFilesByName(name, scope)) {
                if (!file.isDirectory() && matchesSuffix(path, file) && !result.contains(file)) result.add(file);
            }
        }
        return result;
    }

    private static boolean matchesSuffix(String path, VirtualFile file) {
        String filePath = file.getPath();
        if (hasExtension(path)) return filePath.endsWith("/" + path);
        int dot = filePath.lastIndexOf('.');
        int slash = filePath.lastIndexOf('/');
        String withoutExt = dot > slash ? filePath.substring(0, dot) : filePath;
        return withoutExt.endsWith("/" + path);
    }

    /** Extensions tried for paths written without one: the configured view engine first, then the defaults. */
    public static @NotNull List<String> extensions(@NotNull Project project) {
        List<String> result = new ArrayList<>(NjkLookup.names(project, NjkSourceScanner.VIEW_EXTENSION));
        for (String ext : DEFAULT_EXTENSIONS) {
            if (!result.contains(ext)) result.add(ext);
        }
        return result;
    }

    /**
     * Template directories configured in code: {@code nunjucks.configure("views")},
     * {@code new FileSystemLoader([path.join(__dirname, "templates")])}, {@code app.set("views", ...)}.
     */
    public static @NotNull List<VirtualFile> configuredRoots(@NotNull Project project) {
        List<VirtualFile> roots = new ArrayList<>();
        for (String spec : NjkLookup.names(project, NjkSourceScanner.ROOT)) {
            boolean scriptRelative = spec.startsWith(NjkSourceScanner.ROOT_DIR);
            String relative = spec.substring(spec.indexOf(':') + 1);
            while (relative.startsWith("./")) relative = relative.substring(2);
            for (VirtualFile script : NjkLookup.files(project, NjkSourceScanner.ROOT + spec)) {
                VirtualFile base = scriptRelative ? script.getParent() : workingDirectory(project, script);
                if (base == null) continue;
                VirtualFile root = relative.isEmpty() || relative.equals(".") ? base : base.findFileByRelativePath(relative);
                if (root != null && root.isDirectory() && !roots.contains(root)) roots.add(root);
            }
        }
        return roots;
    }

    /** The directory a Node app is usually started from: the closest one with a package.json. */
    private static @Nullable VirtualFile workingDirectory(Project project, VirtualFile script) {
        for (VirtualFile dir = script.getParent(); dir != null; dir = dir.getParent()) {
            if (dir.findChild("package.json") != null) return dir;
        }
        return ProjectUtil.guessProjectDir(project);
    }

    /** Whether {@code path}, written in {@code fromFile}, refers to {@code target}. */
    public static boolean refersTo(@NotNull String path, @NotNull VirtualFile target, @NotNull Project project,
                                   @Nullable VirtualFile fromFile) {
        if (!NjkSourceScanner.baseName(path).equals(NjkSourceScanner.baseName(target.getName()))) return false;
        return resolve(path, project, fromFile).contains(target);
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    public static @Nullable NjkFile njkFile(@NotNull Project project, @Nullable VirtualFile file) {
        if (file == null || !file.isValid()) return null;
        PsiFile psi = PsiManager.getInstance(project).findFile(file);
        if (psi == null) return null;
        PsiFile njk = psi.getViewProvider().getPsi(NjkLanguage.INSTANCE);
        return njk instanceof NjkFile f ? f : null;
    }

    public static @NotNull List<NjkFile> resolveToNjkFiles(@NotNull String path, @NotNull PsiFile from) {
        List<NjkFile> result = new ArrayList<>();
        for (VirtualFile vf : resolve(path, from)) {
            NjkFile f = njkFile(from.getProject(), vf);
            if (f != null) result.add(f);
        }
        return result;
    }

    // ------------------------------------------------------------------ template roots

    /** Directories that template paths seem to be relative to, most used first. */
    public static @NotNull List<VirtualFile> templateRoots(@NotNull Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () ->
                CachedValueProvider.Result.create(computeRoots(project), PsiModificationTracker.getInstance(project)));
    }

    private static List<VirtualFile> computeRoots(Project project) {
        if (DumbService.isDumb(project)) return List.of();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> templates = FileTypeIndex.getFiles(NjkFileType.INSTANCE, scope);
        Map<VirtualFile, Integer> counts = new LinkedHashMap<>();
        for (VirtualFile root : configuredRoots(project)) counts.put(root, Integer.MAX_VALUE);
        int scanned = 0;
        for (VirtualFile file : templates) {
            if (scanned++ > MAX_FILES_FOR_ROOTS) break;
            CharSequence text = LoadTextUtil.loadText(file);
            for (NjkSourceScanner.Entry entry : NjkSourceScanner.scan(text, file.getExtension())) {
                if (!entry.key().startsWith(NjkSourceScanner.TEMPLATE) || isRelative(entry.name())) continue;
                String path = normalize(entry.name());
                List<VirtualFile> matches = suffixCandidates(project, path);
                if (matches.size() != 1) continue;
                VirtualFile root = matches.get(0).getParent();
                for (int i = path.split("/").length - 1; i > 0 && root != null; i--) root = root.getParent();
                if (root != null) counts.merge(root, 1, Integer::sum);
            }
        }
        for (VirtualFile dir : FilenameIndex.getVirtualFilesByName("_includes", scope)) {
            if (dir.isDirectory()) counts.putIfAbsent(dir, 0);
        }
        List<VirtualFile> roots = new ArrayList<>(counts.keySet());
        roots.sort(Comparator.comparingInt((VirtualFile f) -> -counts.get(f)));
        if (roots.isEmpty() && !templates.isEmpty()) {
            VirtualFile common = null;
            for (VirtualFile file : templates) {
                if (file.getPath().contains("/node_modules/")) continue;
                VirtualFile parent = file.getParent();
                common = common == null ? parent : VfsUtilCore.getCommonAncestor(common, parent);
                if (common == null) break;
            }
            if (common != null) roots.add(common);
        }
        return roots;
    }

    /** Paths to offer when completing a template name, mapped to their files. */
    public static @NotNull Map<String, VirtualFile> completionVariants(@NotNull PsiFile from) {
        Project project = from.getProject();
        Map<String, VirtualFile> result = new TreeMap<>();
        if (DumbService.isDumb(project)) return result;
        VirtualFile self = from.getOriginalFile().getVirtualFile();
        List<VirtualFile> roots = templateRoots(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Set<VirtualFile> files = new LinkedHashSet<>(FileTypeIndex.getFiles(NjkFileType.INSTANCE, scope));
        for (VirtualFile html : FilenameIndex.getAllFilesByExt(project, "html", scope)) {
            for (VirtualFile root : roots) {
                if (VfsUtilCore.isAncestor(root, html, true)) {
                    files.add(html);
                    break;
                }
            }
        }
        for (VirtualFile file : files) {
            if (file.equals(self) || file.getPath().contains("/node_modules/")) continue;
            String path = pathFor(file, roots, self);
            if (path != null) result.put(path, file);
        }
        return result;
    }

    /** How code in {@code from} should refer to {@code template}: relative to the closest template root. */
    public static @Nullable String pathFor(@NotNull VirtualFile template, @NotNull Project project, @Nullable VirtualFile from) {
        return pathFor(template, templateRoots(project), from);
    }

    private static @Nullable String pathFor(VirtualFile template, List<VirtualFile> roots, @Nullable VirtualFile from) {
        VirtualFile best = null;
        for (VirtualFile root : roots) {
            if (VfsUtilCore.isAncestor(root, template, true) && (best == null || VfsUtilCore.isAncestor(best, root, true))) {
                best = root;
            }
        }
        if (best != null) return VfsUtilCore.getRelativePath(template, best, '/');
        VirtualFile dir = from != null ? from.getParent() : null;
        if (dir != null && VfsUtilCore.isAncestor(dir, template, true)) {
            return "./" + VfsUtilCore.getRelativePath(template, dir, '/');
        }
        return null;
    }
}
