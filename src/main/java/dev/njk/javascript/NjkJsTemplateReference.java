package dev.njk.javascript;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import dev.njk.resolve.NjkTemplatePaths;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The template path in {@code res.render("profile", ...)} or {@code env.getTemplate("x.njk")}. */
final class NjkJsTemplateReference extends PsiPolyVariantReferenceBase<JSLiteralExpression> {
    NjkJsTemplateReference(@NotNull JSLiteralExpression element, @NotNull TextRange range) {
        super(element, range, true);
    }

    private String path() {
        return getRangeInElement().substring(getElement().getText());
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        PsiFile file = getElement().getContainingFile();
        List<PsiFile> result = new ArrayList<>();
        for (VirtualFile vf : NjkTemplatePaths.resolve(path(), file)) {
            PsiFile template = file.getManager().findFile(vf);
            if (template != null) result.add(template);
        }
        return PsiElementResolveResult.createResults(result);
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<Object> variants = new ArrayList<>();
        for (Map.Entry<String, VirtualFile> entry : NjkTemplatePaths.completionVariants(getElement().getContainingFile()).entrySet()) {
            variants.add(LookupElementBuilder.create(entry.getKey()).withLookupString(entry.getValue().getName())
                    .withIcon(entry.getValue().getFileType().getIcon()).withTypeText("template", true));
        }
        return variants.toArray();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newFileName) throws IncorrectOperationException {
        String path = path();
        int slash = path.lastIndexOf('/');
        String oldName = path.substring(slash + 1);
        String newName = oldName.indexOf('.') < 0 && newFileName.indexOf('.') > 0
                ? newFileName.substring(0, newFileName.lastIndexOf('.'))
                : newFileName;
        return ElementManipulators.handleContentChange(getElement(), getRangeInElement(), path.substring(0, slash + 1) + newName);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        if (!(element instanceof PsiFile file) || file.getVirtualFile() == null) return super.bindToElement(element);
        VirtualFile from = getElement().getContainingFile().getOriginalFile().getVirtualFile();
        String newPath = NjkTemplatePaths.pathFor(file.getVirtualFile(), element.getProject(), from);
        if (newPath == null) return getElement();
        if (path().indexOf('.') < 0 && newPath.lastIndexOf('.') > newPath.lastIndexOf('/')) {
            newPath = newPath.substring(0, newPath.lastIndexOf('.'));
        }
        return ElementManipulators.handleContentChange(getElement(), getRangeInElement(), newPath);
    }
}
