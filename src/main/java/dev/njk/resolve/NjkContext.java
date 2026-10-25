package dev.njk.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import dev.njk.NjkFile;
import dev.njk.index.NjkSourceScanner;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.psi.NjkNamedElementBase;
import dev.njk.psi.NjkTag;
import dev.njk.types.NjkTypeProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds where variables used in a template, but not declared in it, come from:
 * <ul>
 *   <li>templates that include it (names visible at the include tag)</li>
 *   <li>templates that extend it, and the templates it extends (top-level {@code set}s)</li>
 *   <li>front matter, and pages that use it as their Eleventy layout</li>
 *   <li>{@code render("template", { ... })} calls in JavaScript/TypeScript</li>
 *   <li>{@code addGlobal()}, {@code app.locals.x = ...} and Eleventy {@code _data} files</li>
 * </ul>
 */
public final class NjkContext {
    /** A name provided to a template from somewhere else. */
    public record Source(@NotNull String name, @NotNull PsiElement target, @NotNull String description) {
    }

    private static final Pattern FRONT_MATTER_KEY = Pattern.compile("(?m)^([A-Za-z_$][\\w$]*)[ \\t]*:");
    private static final Set<String> DATA_EXTENSIONS = Set.of("json", "js", "cjs", "mjs", "ts", "yaml", "yml");
    private static final int MAX_DEPTH = 4;

    private NjkContext() {
    }

    public static @NotNull Map<String, List<Source>> external(@NotNull NjkFile file) {
        return CachedValuesManager.getCachedValue(file, () ->
                CachedValueProvider.Result.create(compute(file), PsiModificationTracker.getInstance(file.getProject())));
    }

    private static Map<String, List<Source>> compute(NjkFile file) {
        Map<String, List<Source>> out = new LinkedHashMap<>();
        collect(file, out, new HashSet<>(), 0);
        Project project = file.getProject();
        for (String name : NjkLookup.names(project, NjkSourceScanner.GLOBAL)) {
            for (NjkSourceTarget target : NjkLookup.targets(project, NjkSourceScanner.GLOBAL + name, "global")) {
                add(out, name, target, target.getDescription());
            }
        }
        collectDataFiles(project, out);
        return out;
    }

    private static void collect(NjkFile file, Map<String, List<Source>> out, Set<VirtualFile> visited, int depth) {
        VirtualFile vf = file.getOriginalFile().getVirtualFile();
        if (vf == null || depth > MAX_DEPTH || !visited.add(vf)) return;
        Project project = file.getProject();

        collectFrontMatter(file, out, depth == 0 ? "front matter" : "front matter of " + file.getName());

        for (NjkFile parent : NjkLookup.parentTemplates(file)) {
            collectTopLevel(parent, out, "set in parent " + parent.getName());
            collectFrontMatter(parent, out, "front matter of " + parent.getName());
        }

        for (NjkTag tag : NjkLookup.referencingTags(file)) {
            PsiFile from = tag.getContainingFile();
            String name = tag.getTagName();
            if ("include".equals(name) || tag.hasWithContext()) {
                String description = "visible where " + from.getName() + " includes it";
                NjkScopes.processLocal(tag, declaration -> {
                    add(out, declaration, description);
                    return true;
                });
                if (from instanceof NjkFile includer) collect(includer, out, visited, depth + 1);
            } else if ("extends".equals(name) && from instanceof NjkFile child) {
                collectTopLevel(child, out, "set in child " + child.getName());
                collectFrontMatter(child, out, "front matter of " + child.getName());
                collectRenderContext(child, out);
            }
        }

        collectLayoutUsers(file, vf, out);
        collectRenderContext(file, out);
    }

    private static void collectTopLevel(NjkFile file, Map<String, List<Source>> out, String description) {
        NjkScopes.processTopLevel(file, declaration -> {
            add(out, declaration, description);
            return true;
        });
    }

    private static void collectFrontMatter(PsiFile file, Map<String, List<Source>> out, String description) {
        PsiElement first = file.getFirstChild();
        if (first == null || first.getNode().getElementType() != NjkTokenTypes.FRONT_MATTER) return;
        collectFrontMatterKeys(file, first.getText(), first.getTextOffset(), out, description);
    }

    private static void collectFrontMatterKeys(PsiFile file, CharSequence text, int base,
                                               Map<String, List<Source>> out, String description) {
        Matcher m = FRONT_MATTER_KEY.matcher(text);
        while (m.find()) {
            String key = m.group(1);
            add(out, key, new NjkSourceTarget(file, base + m.start(1), key, description), description);
        }
    }

    /** Eleventy pages with {@code layout: thisfile} in their front matter provide their data to the layout. */
    private static void collectLayoutUsers(NjkFile layout, VirtualFile layoutFile, Map<String, List<Source>> out) {
        Project project = layout.getProject();
        for (VirtualFile page : NjkLookup.files(project, NjkSourceScanner.LAYOUT + NjkSourceScanner.baseName(layoutFile.getName()))) {
            PsiFile psi = PsiManager.getInstance(project).findFile(page);
            if (psi == null) continue;
            CharSequence text = psi.getViewProvider().getContents();
            int end = NjkSourceScanner.frontMatterEnd(text);
            if (end < 0) continue;
            boolean usesLayout = false;
            for (NjkSourceScanner.Entry entry : NjkSourceScanner.scan(text, page.getExtension())) {
                if (entry.key().startsWith(NjkSourceScanner.LAYOUT)
                        && NjkTemplatePaths.refersTo(entry.name(), layoutFile, project, page)) {
                    usesLayout = true;
                    break;
                }
            }
            if (usesLayout) {
                collectFrontMatterKeys(psi, text.subSequence(0, end), 0, out, "front matter of page " + page.getName());
            }
        }
    }

    /** Keys of object literals passed to {@code render("this template", {...})}. */
    private static void collectRenderContext(NjkFile file, Map<String, List<Source>> out) {
        VirtualFile vf = file.getOriginalFile().getVirtualFile();
        if (vf == null) return;
        Project project = file.getProject();
        for (VirtualFile script : NjkLookup.files(project, NjkSourceScanner.TEMPLATE + NjkSourceScanner.baseName(vf.getName()))) {
            if (!NjkSourceScanner.isScript(script.getExtension())) continue;
            PsiFile psi = PsiManager.getInstance(project).findFile(script);
            if (psi == null) continue;
            CharSequence text = psi.getViewProvider().getContents();
            for (NjkSourceScanner.Entry entry : NjkSourceScanner.scan(text, script.getExtension())) {
                if (!entry.key().startsWith(NjkSourceScanner.TEMPLATE)) continue;
                if (!NjkTemplatePaths.refersTo(entry.name(), vf, project, script)) continue;
                List<NjkTypeProvider.ContextVariable> typed = null;
                for (NjkTypeProvider provider : NjkTypeProvider.EP_NAME.getExtensionList()) {
                    typed = provider.renderContext(psi, entry.offset());
                    if (typed != null) break;
                }
                if (typed != null) {
                    for (NjkTypeProvider.ContextVariable variable : typed) {
                        add(out, variable.name(), variable.target(), variable.description());
                    }
                    continue;
                }
                int afterString = entry.offset() + entry.name().length() + 1;
                String description = "render() context in " + script.getName();
                for (NjkJsObjects.Key key : NjkJsObjects.keysOfSecondArgument(text, afterString)) {
                    add(out, key.name(), new NjkSourceTarget(psi, key.offset(), key.name(), description), description);
                }
            }
        }
    }

    /** Eleventy global data: {@code _data/site.json} provides {@code site}. */
    private static void collectDataFiles(Project project, Map<String, List<Source>> out) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (VirtualFile dir : FilenameIndex.getVirtualFilesByName("_data", scope)) {
            if (!dir.isDirectory()) continue;
            for (VirtualFile child : dir.getChildren()) {
                String name = child.isDirectory() ? child.getName() : child.getNameWithoutExtension();
                if (!child.isDirectory() && !DATA_EXTENSIONS.contains(child.getExtension())) continue;
                PsiElement target = child.isDirectory()
                        ? PsiManager.getInstance(project).findDirectory(child)
                        : PsiManager.getInstance(project).findFile(child);
                if (target != null) add(out, name, target, "Eleventy data in " + dir.getName() + "/" + child.getName());
            }
        }
    }

    private static void add(Map<String, List<Source>> out, PsiNamedElement declaration, String description) {
        String name = declaration.getName();
        if (name == null) return;
        String kind = declaration instanceof NjkNamedElementBase named ? named.getKindDescription() + ", " : "";
        add(out, name, declaration, kind + description);
    }

    private static void add(Map<String, List<Source>> out, String name, PsiElement target, String description) {
        List<Source> list = out.computeIfAbsent(name, k -> new ArrayList<>());
        for (Source existing : list) {
            if (existing.target().equals(target)) return;
        }
        list.add(new Source(name, target, description));
    }
}
