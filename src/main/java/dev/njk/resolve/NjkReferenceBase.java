package dev.njk.resolve;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base for Nunjucks references: soft (never reported as unresolved, since most data comes from code the IDE
 * can't see), cached, and renamed by replacing the identifier leaf. Completion is done by the contributor.
 */
public abstract class NjkReferenceBase<T extends PsiElement> extends PsiPolyVariantReferenceBase<T> {
    private static final ResolveCache.PolyVariantResolver<NjkReferenceBase<?>> RESOLVER =
            (ref, incomplete) -> PsiElementResolveResult.createResults(ref.computeTargets());

    protected NjkReferenceBase(@NotNull T element, @NotNull TextRange range) {
        super(element, range, true);
    }

    protected abstract @NotNull List<? extends PsiElement> computeTargets();

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, RESOLVER, false, incompleteCode);
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        PsiElement leaf = getElement().findElementAt(getRangeInElement().getStartOffset());
        if (leaf instanceof LeafPsiElement l && l.getTextRange().equals(getAbsoluteRange())) {
            l.replaceWithText(newElementName);
            return getElement();
        }
        return super.handleElementRename(newElementName);
    }
}
