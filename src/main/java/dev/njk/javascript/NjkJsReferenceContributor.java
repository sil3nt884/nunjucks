package dev.njk.javascript;

import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/** Makes template paths in render()/getTemplate() calls navigable, completable and renamable. */
public final class NjkJsReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(JSLiteralExpression.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                JSLiteralExpression literal = (JSLiteralExpression) element;
                if (!literal.isQuotedLiteral() || literal.getStringValue() == null) return PsiReference.EMPTY_ARRAY;
                String method = NjkJsPsi.methodName(NjkJsPsi.callWithFirstArgument(literal));
                if (method == null || !NjkJsPsi.TEMPLATE_METHODS.contains(method)) return PsiReference.EMPTY_ARRAY;
                int length = literal.getTextLength();
                if (length < 2) return PsiReference.EMPTY_ARRAY;
                return new PsiReference[]{new NjkJsTemplateReference(literal, new TextRange(1, length - 1))};
            }
        });
    }
}
