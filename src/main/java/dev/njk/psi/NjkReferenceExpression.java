package dev.njk.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import dev.njk.resolve.NjkVariableReference;
import org.jetbrains.annotations.NotNull;

/** A plain name used in an expression, e.g. {@code user} in {@code {{ user.name }}}. */
public final class NjkReferenceExpression extends NjkPsiElement {
    public NjkReferenceExpression(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull String getName() {
        return getText();
    }

    @Override
    public PsiReference getReference() {
        return new NjkVariableReference(this);
    }
}
