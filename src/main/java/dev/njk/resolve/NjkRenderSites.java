package dev.njk.resolve;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import dev.njk.NjkFile;
import dev.njk.index.NjkSourceScanner;
import dev.njk.psi.NjkTag;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds everywhere a template is rendered from: render() calls, other mentions in code, and other templates. */
public final class NjkRenderSites {
    public enum Kind {
        RENDER("render()"), CODE("referenced in code"), INCLUDE("include"), EXTENDS("extends"), IMPORT("import"),
        LAYOUT("Eleventy layout");

        public final String description;

        Kind(String description) {
            this.description = description;
        }
    }

    /** A place that renders the template, at {@code offset} in {@code file}. */
    public record Site(@NotNull PsiFile file, int offset, @NotNull Kind kind, @NotNull String preview) {
        public int line() {
            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            return document != null && offset <= document.getTextLength() ? document.getLineNumber(offset) + 1 : 0;
        }
    }

    private static final Pattern QUOTED = Pattern.compile("([\"'`])([^\"'`\\r\\n$]{1,200})\\1");

    private NjkRenderSites() {
    }

    public static @NotNull List<Site> find(@NotNull NjkFile template) {
        VirtualFile vf = template.getOriginalFile().getVirtualFile();
        if (vf == null) return List.of();
        Project project = template.getProject();
        String baseName = NjkSourceScanner.baseName(vf.getName());
        List<Site> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // render("...") calls found by the index
        for (VirtualFile script : NjkLookup.files(project, NjkSourceScanner.TEMPLATE + baseName)) {
            if (!NjkSourceScanner.isScript(script.getExtension())) continue;
            PsiFile psi = PsiManager.getInstance(project).findFile(script);
            if (psi == null) continue;
            CharSequence text = psi.getViewProvider().getContents();
            for (NjkSourceScanner.Entry entry : NjkSourceScanner.scan(text, script.getExtension())) {
                if (entry.key().startsWith(NjkSourceScanner.TEMPLATE)
                        && NjkTemplatePaths.refersTo(entry.name(), vf, project, script)
                        && seen.add(script.getPath() + ":" + entry.offset())) {
                    result.add(new Site(psi, entry.offset(), Kind.RENDER, lineAt(text, entry.offset())));
                }
            }
        }

        // other string literals naming the template, e.g. constants passed to render() later
        GlobalSearchScope scripts = new DelegatingGlobalSearchScope(GlobalSearchScope.projectScope(project)) {
            @Override
            public boolean contains(@NotNull VirtualFile file) {
                return super.contains(file) && NjkSourceScanner.isScript(file.getExtension());
            }
        };
        PsiSearchHelper.getInstance(project).processCandidateFilesForText(scripts, UsageSearchContext.ANY, true, baseName, script -> {
            PsiFile psi = PsiManager.getInstance(project).findFile(script);
            if (psi == null) return true;
            CharSequence text = psi.getViewProvider().getContents();
            Matcher m = QUOTED.matcher(text);
            while (m.find()) {
                String value = m.group(2);
                if (!NjkSourceScanner.baseName(value).equals(baseName)) continue;
                if (seen.contains(script.getPath() + ":" + m.start(2))) continue;
                if (NjkTemplatePaths.refersTo(value, vf, project, script)) {
                    seen.add(script.getPath() + ":" + m.start(2));
                    result.add(new Site(psi, m.start(2), Kind.CODE, lineAt(text, m.start(2))));
                }
            }
            return true;
        });

        // templates that include, extend or import it
        for (NjkTag tag : NjkLookup.referencingTags(template)) {
            Kind kind = switch (Objects.requireNonNullElse(tag.getTagName(), "")) {
                case "extends" -> Kind.EXTENDS;
                case "include" -> Kind.INCLUDE;
                default -> Kind.IMPORT;
            };
            PsiFile file = tag.getContainingFile();
            result.add(new Site(file, tag.getTextOffset(), kind, tag.getText()));
        }

        // Eleventy pages that use it as their layout
        for (VirtualFile page : NjkLookup.files(project, NjkSourceScanner.LAYOUT + baseName)) {
            PsiFile psi = PsiManager.getInstance(project).findFile(page);
            if (psi == null) continue;
            CharSequence text = psi.getViewProvider().getContents();
            for (NjkSourceScanner.Entry entry : NjkSourceScanner.scan(text, page.getExtension())) {
                if (entry.key().startsWith(NjkSourceScanner.LAYOUT) && NjkTemplatePaths.refersTo(entry.name(), vf, project, page)) {
                    result.add(new Site(psi, entry.offset(), Kind.LAYOUT, lineAt(text, entry.offset())));
                }
            }
        }

        result.sort(Comparator.comparing(Site::kind).thenComparing(s -> s.file().getName()).thenComparingInt(Site::offset));
        return result;
    }

    private static String lineAt(CharSequence text, int offset) {
        int start = offset;
        while (start > 0 && text.charAt(start - 1) != '\n') start--;
        int end = offset;
        while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
        String line = text.subSequence(start, end).toString().trim();
        return line.length() > 100 ? line.substring(0, 97) + "..." : line;
    }
}
