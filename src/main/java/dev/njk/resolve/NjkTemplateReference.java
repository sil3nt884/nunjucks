package dev.njk.resolve;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import dev.njk.psi.NjkStringLiteral;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** The path in extends/include/import/from, pointing at the template file. */
public final class NjkTemplateReference extends NjkReferenceBase<NjkStringLiteral> {
    public NjkTemplateReference(@NotNull NjkStringLiteral element) {
        super(element, element.getValueRange());
    }

    @Override
    protected @NotNull List<? extends PsiElement> computeTargets() {
        List<PsiFile> result = new ArrayList<>();
        NjkStringLiteral element = getElement();
        for (VirtualFile vf : NjkTemplatePaths.resolve(element.getValue(), element.getContainingFile())) {
            PsiFile file = PsiManager.getInstance(element.getProject()).findFile(vf);
            if (file != null) result.add(file);
        }
        return result;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newFileName) throws IncorrectOperationException {
        NjkStringLiteral element = getElement();
        String path = element.getValue();
        int slash = path.lastIndexOf('/');
        String oldName = path.substring(slash + 1);
        String newName = newFileName;
        if (oldName.indexOf('.') < 0 && newName.indexOf('.') > 0) newName = newName.substring(0, newName.lastIndexOf('.'));
        String text = element.getText();
        char quote = text.isEmpty() ? '"' : text.charAt(0);
        PsiElement leaf = element.getFirstChild();
        if (leaf instanceof LeafPsiElement l) {
            l.replaceWithText(quote + path.substring(0, slash + 1) + newName + quote);
        }
        return element;
    }
}
