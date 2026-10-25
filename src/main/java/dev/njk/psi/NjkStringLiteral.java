package dev.njk.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import dev.njk.parser.NjkElementTypes;
import dev.njk.resolve.NjkTemplateReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NjkStringLiteral extends NjkPsiElement {
    public NjkStringLiteral(@NotNull ASTNode node) {
        super(node);
    }

    /** Range of the string contents without the quotes, relative to this element. */
    public @NotNull TextRange getValueRange() {
        String text = getText();
        if (text.isEmpty()) return TextRange.EMPTY_RANGE;
        char quote = text.charAt(0);
        int end = text.length() > 1 && text.charAt(text.length() - 1) == quote ? text.length() - 1 : text.length();
        return new TextRange(1, Math.max(1, end));
    }

    public @NotNull String getValue() {
        String raw = getValueRange().substring(getText());
        if (raw.indexOf('\\') < 0) return raw;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char n = raw.charAt(++i);
                sb.append(switch (n) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> n;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The tag whose template argument this string is (directly, or as an element of an include list). */
    public @Nullable NjkTag getTemplateTag() {
        PsiElement parent = getParent();
        if (parent != null && parent.getNode().getElementType() == NjkElementTypes.LIST_LITERAL) {
            parent = parent.getParent();
        }
        if (parent instanceof NjkTag tag && tag.referencesTemplate()) return tag;
        return null;
    }

    @Override
    public PsiReference getReference() {
        return getTemplateTag() != null ? new NjkTemplateReference(this) : null;
    }
}
