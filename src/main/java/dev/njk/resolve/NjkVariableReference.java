package dev.njk.resolve;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import dev.njk.psi.NjkReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class NjkVariableReference extends NjkReferenceBase<NjkReferenceExpression> {
    public NjkVariableReference(@NotNull NjkReferenceExpression element) {
        super(element, TextRange.from(0, element.getTextLength()));
    }

    @Override
    protected @NotNull List<? extends PsiElement> computeTargets() {
        return NjkResolver.resolveVariable(getElement());
    }
}
