package dev.njk.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/** The test name in {@code value is name}. */
public final class NjkTestName extends NjkPsiElement {
    public NjkTestName(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull String getName() {
        return getText();
    }
}
