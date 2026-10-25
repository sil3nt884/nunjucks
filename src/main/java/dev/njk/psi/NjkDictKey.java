package dev.njk.psi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** A key of a dict literal: {@code {key: value}} or {@code {"key": value}}. */
public final class NjkDictKey extends NjkPsiElement implements PsiNamedElement {
    public NjkDictKey(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull String getName() {
        String text = getText();
        if (text.length() >= 2 && (text.charAt(0) == '"' || text.charAt(0) == '\'')) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    @Override
    public PsiNamedElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Renaming dict keys is not supported");
    }

    @Override
    public Icon getIcon(int flags) {
        return AllIcons.Nodes.Field;
    }
}
