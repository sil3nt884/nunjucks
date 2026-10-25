package dev.njk.highlight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.parser.NjkElementTypes;
import dev.njk.parser.NjkTagSpec;
import dev.njk.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** Semantic highlighting that the lexer can't do: filters, tests, declarations, members and calls. */
public final class NjkAnnotator implements Annotator {
    private static final Set<String> SOFT_KEYWORDS = Set.of("as", "import", "with", "without", "context", "ignore", "missing");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof NjkFilterName) {
            highlight(holder, element, NjkHighlightColors.FILTER);
        } else if (element instanceof NjkTestName) {
            highlight(holder, element, NjkHighlightColors.TEST);
        } else if (element instanceof NjkVariable variable) {
            TextAttributesKey key = switch (variable.getKind()) {
                case MACRO -> NjkHighlightColors.MACRO_DECLARATION;
                case PARAMETER, CALL_PARAMETER -> NjkHighlightColors.PARAMETER;
                default -> NjkHighlightColors.VARIABLE_DECLARATION;
            };
            highlight(holder, element, key);
        } else if (element instanceof NjkImportedName) {
            highlight(holder, element, NjkHighlightColors.FUNCTION_CALL);
        } else if (element instanceof NjkBlockName) {
            highlight(holder, element, NjkHighlightColors.BLOCK_NAME);
        } else if (element instanceof NjkDictKey) {
            highlight(holder, element, NjkHighlightColors.MEMBER);
        } else if (element instanceof NjkMemberExpression member) {
            PsiElement name = member.getNameElement();
            if (name != null) highlight(holder, name, isCallee(member) ? NjkHighlightColors.FUNCTION_CALL : NjkHighlightColors.MEMBER);
        } else if (element instanceof NjkReferenceExpression reference) {
            if (isCallee(reference)) {
                highlight(holder, element, NjkHighlightColors.FUNCTION_CALL);
            } else if (SOFT_KEYWORDS.contains(reference.getName())) {
                highlight(holder, element, NjkHighlightColors.IDENTIFIER);
            }
        } else if (element instanceof NjkTag tag) {
            PsiElement name = tag.getTagNameElement();
            if (name != null && !NjkTagSpec.isBuiltin(name.getText())) highlight(holder, name, NjkHighlightColors.CUSTOM_TAG);
        } else if (element.getNode().getElementType() == NjkElementTypes.KEYWORD_ARGUMENT) {
            PsiElement name = element.getFirstChild();
            if (name != null && name.getNode().getElementType() == NjkTokenTypes.IDENTIFIER) {
                highlight(holder, name, NjkHighlightColors.PARAMETER);
            }
        }
    }

    private static boolean isCallee(PsiElement expression) {
        PsiElement parent = expression.getParent();
        return parent != null && parent.getNode().getElementType() == NjkElementTypes.CALL_EXPRESSION
                && parent.getFirstChild() == expression;
    }

    private static void highlight(AnnotationHolder holder, PsiElement element, TextAttributesKey key) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(key).create();
    }
}
