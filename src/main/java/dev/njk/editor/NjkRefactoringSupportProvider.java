package dev.njk.editor;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import dev.njk.psi.NjkVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NjkRefactoringSupportProvider extends RefactoringSupportProvider {
    @Override
    public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, @Nullable PsiElement context) {
        return element instanceof NjkVariable;
    }
}
