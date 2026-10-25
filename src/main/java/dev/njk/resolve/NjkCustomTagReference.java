package dev.njk.resolve;

import com.intellij.psi.PsiElement;
import dev.njk.index.NjkSourceScanner;
import dev.njk.psi.NjkTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Resolves custom tags and Eleventy shortcodes to where they are registered. */
public final class NjkCustomTagReference extends NjkReferenceBase<NjkTag> {
    private final String name;

    public NjkCustomTagReference(@NotNull NjkTag element, @NotNull PsiElement nameElement) {
        super(element, nameElement.getTextRangeInParent());
        this.name = nameElement.getText();
    }

    @Override
    protected @NotNull List<? extends PsiElement> computeTargets() {
        var project = getElement().getProject();
        List<NjkSourceTarget> result = new ArrayList<>();
        result.addAll(NjkLookup.targets(project, NjkSourceScanner.TAG + name, "tag"));
        result.addAll(NjkLookup.targets(project, NjkSourceScanner.PAIRED_TAG + name, "paired shortcode"));
        if (name.startsWith("end") && name.length() > 3) {
            result.addAll(NjkLookup.targets(project, NjkSourceScanner.PAIRED_TAG + name.substring(3), "paired shortcode"));
        }
        return result;
    }
}
