package dev.njk.psi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.resolve.NjkImportedNameReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** {@code name} in {@code {% from "forms.njk" import name as alias %}}. */
public final class NjkImportedName extends NjkNamedElementBase {
    public NjkImportedName(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable NjkVariable getAlias() {
        PsiElement specifier = getParent();
        return specifier != null ? PsiTreeUtil.getChildOfType(specifier, NjkVariable.class) : null;
    }

    public @Nullable NjkTag getFromTag() {
        return PsiTreeUtil.getParentOfType(this, NjkTag.class);
    }

    @Override
    public @NotNull String getKindDescription() {
        return "imported macro";
    }

    @Override
    public PsiReference getReference() {
        PsiElement id = getNameIdentifier();
        return id != null ? new NjkImportedNameReference(this) : null;
    }

    @Override
    public Icon getIcon(int flags) {
        return AllIcons.Nodes.Function;
    }
}
