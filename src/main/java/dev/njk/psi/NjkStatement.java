package dev.njk.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.parser.NjkElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A block tag together with its body and closing tag, e.g. {@code {% if %}...{% endif %}}. */
public final class NjkStatement extends NjkPsiElement {
    public NjkStatement(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable NjkTag getOpenTag() {
        return PsiTreeUtil.getChildOfType(this, NjkTag.class);
    }

    public @Nullable String getTagName() {
        NjkTag open = getOpenTag();
        return open != null ? open.getTagName() : null;
    }

    public @Nullable NjkTag getEndTag() {
        PsiElement last = getLastChild();
        while (last != null && !(last instanceof NjkTag)) {
            if (!(last instanceof PsiWhiteSpace) && !(last instanceof PsiErrorElement)) {
                // template text after the last tag means the block was never closed
                return null;
            }
            last = last.getPrevSibling();
        }
        if (last instanceof NjkTag tag && tag != getOpenTag()) {
            String name = tag.getTagName();
            if (name != null && name.startsWith("end")) return tag;
        }
        return null;
    }

    /** Whether names declared inside stay inside (for, macro, call and block bodies). */
    public boolean isScoping() {
        return NjkElementTypes.SCOPING_STATEMENTS.contains(getNode().getElementType());
    }

    public boolean is(@NotNull IElementType type) {
        return getNode().getElementType() == type;
    }
}
