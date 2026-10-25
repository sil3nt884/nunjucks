package dev.njk.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import dev.njk.NjkFile;
import dev.njk.psi.NjkImportedName;
import dev.njk.psi.NjkTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** {@code field} in {@code {% from "forms.njk" import field %}} points at the macro in forms.njk. */
public final class NjkImportedNameReference extends NjkReferenceBase<NjkImportedName> {
    public NjkImportedNameReference(@NotNull NjkImportedName element) {
        super(element, Objects.requireNonNull(element.getNameIdentifier()).getTextRangeInParent());
    }

    @Override
    protected @NotNull List<? extends PsiElement> computeTargets() {
        NjkImportedName element = getElement();
        String name = element.getName();
        NjkTag tag = element.getFromTag();
        List<PsiElement> result = new ArrayList<>();
        if (name == null || tag == null) return result;
        for (NjkFile file : NjkLookup.targetsOf(tag)) {
            PsiElement macro = NjkScopes.findMacro(file, name);
            if (macro != null) {
                result.add(macro);
                continue;
            }
            NjkScopes.processTopLevel(file, (PsiNamedElement d) -> {
                if (name.equals(d.getName())) {
                    result.add(d);
                    return false;
                }
                return true;
            });
        }
        return result;
    }
}
