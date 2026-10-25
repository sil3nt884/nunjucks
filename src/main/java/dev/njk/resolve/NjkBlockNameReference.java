package dev.njk.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.psi.NjkBlockName;
import dev.njk.psi.NjkStatement;
import dev.njk.psi.NjkTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * In {@code {% block x %}}, points at the block it overrides in a parent template;
 * in {@code {% endblock x %}}, points at the opening block.
 */
public final class NjkBlockNameReference extends NjkReferenceBase<NjkBlockName> {
    public NjkBlockNameReference(@NotNull NjkBlockName element) {
        super(element, Objects.requireNonNull(element.getNameIdentifier()).getTextRangeInParent());
    }

    @Override
    protected @NotNull List<? extends PsiElement> computeTargets() {
        NjkBlockName element = getElement();
        if (element.isDefinition()) return NjkResolver.overriddenBlocks(element);
        NjkStatement statement = PsiTreeUtil.getParentOfType(element, NjkStatement.class);
        NjkTag open = statement != null ? statement.getOpenTag() : null;
        NjkBlockName opening = open != null ? PsiTreeUtil.getChildOfType(open, NjkBlockName.class) : null;
        return opening != null ? List.of(opening) : List.of();
    }
}
