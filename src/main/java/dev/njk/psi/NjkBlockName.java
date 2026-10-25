package dev.njk.psi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import dev.njk.resolve.NjkBlockNameReference;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** {@code name} in {@code {% block name %}} or {@code {% endblock name %}}. */
public final class NjkBlockName extends NjkNamedElementBase {
    public NjkBlockName(@NotNull ASTNode node) {
        super(node);
    }

    public boolean isDefinition() {
        return getParent() instanceof NjkTag tag && "block".equals(tag.getTagName());
    }

    @Override
    public @NotNull String getKindDescription() {
        return "block";
    }

    @Override
    public PsiReference getReference() {
        return new NjkBlockNameReference(this);
    }

    @Override
    public Icon getIcon(int flags) {
        return AllIcons.Nodes.Tag;
    }
}
