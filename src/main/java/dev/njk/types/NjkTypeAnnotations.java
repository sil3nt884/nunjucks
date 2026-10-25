package dev.njk.types;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Type annotations in template comments, for values whose type can't be inferred:
 * <pre>
 * {# @type {import("../src/models").User} user #}
 * {# @type posts: Post[] #}
 * {# @param {Post} post #}   (same thing, reads better above a macro)
 * </pre>
 */
public final class NjkTypeAnnotations {
    /**
     * @param nameOffset offset of the annotated name in the file
     */
    public record Annotation(@NotNull String name, @NotNull String typeText, @NotNull PsiComment comment, int nameOffset) {
    }

    private static final Pattern JSDOC_STYLE = Pattern.compile("@(?:type|var|param)\\s+\\{([^}]+)}\\s*([A-Za-z_$][\\w$]*)");
    private static final Pattern COLON_STYLE =
            Pattern.compile("@(?:type|var|param)\\s+([A-Za-z_$][\\w$]*)\\s*:\\s*([^\\n#]+?)\\s*(?=$|\\n|#)");

    private NjkTypeAnnotations() {
    }

    public static @NotNull List<Annotation> annotations(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(scan(file), file));
    }

    private static List<Annotation> scan(PsiFile file) {
        List<Annotation> result = new ArrayList<>();
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(file, PsiComment.class)) {
            String text = comment.getText();
            if (!text.contains("@")) continue;
            int base = comment.getTextRange().getStartOffset();
            Matcher m = JSDOC_STYLE.matcher(text);
            while (m.find()) result.add(new Annotation(m.group(2), m.group(1).trim(), comment, base + m.start(2)));
            m = COLON_STYLE.matcher(text);
            while (m.find()) result.add(new Annotation(m.group(1), m.group(2).trim(), comment, base + m.start(1)));
        }
        return result;
    }

    /** The annotation for {@code name} that applies at {@code place}: the nearest one before it, else the first. */
    public static @Nullable Annotation find(@NotNull PsiElement place, @NotNull String name) {
        PsiFile file = place.getContainingFile();
        if (file == null) return null;
        int offset = place.getTextRange().getStartOffset();
        Annotation before = null;
        Annotation after = null;
        for (Annotation a : annotations(file)) {
            if (!a.name().equals(name)) continue;
            if (a.nameOffset() < offset) {
                before = a;
            } else if (after == null) {
                after = a;
            }
        }
        return before != null ? before : after;
    }

    public static @Nullable NjkType typeFor(@NotNull PsiElement place, @NotNull String name) {
        Annotation annotation = find(place, name);
        return annotation != null ? resolve(annotation) : null;
    }

    public static @NotNull NjkType resolve(@NotNull Annotation annotation) {
        for (NjkTypeProvider provider : NjkTypeProvider.EP_NAME.getExtensionList()) {
            NjkType type = provider.resolveTypeText(annotation.typeText(), annotation.comment());
            if (type != null) return type;
        }
        return new NjkTemplateTypes.Simple(annotation.typeText());
    }
}
