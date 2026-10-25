package dev.njk.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.resolve.NjkMemberReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code qualifier.name} */
public final class NjkMemberExpression extends NjkPsiElement {
    public NjkMemberExpression(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable PsiElement getQualifier() {
        PsiElement first = getFirstChild();
        return first instanceof NjkPsiElement ? first : null;
    }

    public @Nullable PsiElement getNameElement() {
        PsiElement last = getLastChild();
        return last != null && last.getNode().getElementType() == NjkTokenTypes.IDENTIFIER ? last : null;
    }

    public @Nullable String getMemberName() {
        PsiElement name = getNameElement();
        return name != null ? name.getText() : null;
    }

    @Override
    public PsiReference getReference() {
        PsiElement name = getNameElement();
        return name != null ? new NjkMemberReference(this, name) : null;
    }
}
