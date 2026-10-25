package dev.njk.resolve;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import dev.njk.index.NjkSourceScanner;
import dev.njk.psi.NjkFilterName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Resolves custom filters to their {@code addFilter("name", ...)} registration. */
public final class NjkFilterReference extends NjkReferenceBase<NjkFilterName> {
    public NjkFilterReference(@NotNull NjkFilterName element) {
        super(element, TextRange.from(0, element.getTextLength()));
    }

    @Override
    protected @NotNull List<? extends PsiElement> computeTargets() {
        return NjkLookup.targets(getElement().getProject(), NjkSourceScanner.FILTER + getElement().getName(), "filter");
    }
}
