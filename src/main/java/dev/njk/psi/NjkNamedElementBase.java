package dev.njk.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import dev.njk.lexer.NjkTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** A PSI element whose name is its single identifier child. */
public abstract class NjkNamedElementBase extends NjkPsiElement implements PsiNameIdentifierOwner {
    protected NjkNamedElementBase(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        return findChildByType(NjkTokenTypes.IDENTIFIER);
    }

    @Override
    public @Nullable String getName() {
        PsiElement id = getNameIdentifier();
        return id != null ? id.getText() : null;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        PsiElement id = getNameIdentifier();
        if (id instanceof LeafPsiElement leaf) {
            leaf.replaceWithText(name);
        }
        return this;
    }

    @Override
    public int getTextOffset() {
        PsiElement id = getNameIdentifier();
        return id != null ? id.getTextOffset() : super.getTextOffset();
    }

    /** Short description of what kind of declaration this is, e.g. "macro". */
    public abstract @NotNull String getKindDescription();

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return getName();
            }

            @Override
            public String getLocationString() {
                PsiFile file = getContainingFile();
                return getKindDescription() + (file != null ? " in " + file.getName() : "");
            }

            @Override
            public Icon getIcon(boolean unused) {
                return NjkNamedElementBase.this.getIcon(0);
            }
        };
    }
}
