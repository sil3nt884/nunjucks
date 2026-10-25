package dev.njk.resolve;

import com.intellij.psi.PsiElement;
import dev.njk.psi.NjkMemberExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class NjkMemberReference extends NjkReferenceBase<NjkMemberExpression> {
    public NjkMemberReference(@NotNull NjkMemberExpression element, @NotNull PsiElement name) {
        super(element, name.getTextRangeInParent());
    }

    @Override
    protected @NotNull List<? extends PsiElement> computeTargets() {
        return NjkResolver.resolveMember(getElement());
    }
}
