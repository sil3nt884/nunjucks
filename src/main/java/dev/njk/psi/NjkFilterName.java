package dev.njk.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import dev.njk.resolve.NjkFilterReference;
import org.jetbrains.annotations.NotNull;

/** The filter name in {@code value | name(args)} or {@code {% filter name %}}. */
public final class NjkFilterName extends NjkPsiElement {
    public NjkFilterName(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull String getName() {
        return getText();
    }

    @Override
    public PsiReference getReference() {
        return new NjkFilterReference(this);
    }
}
