package dev.njk.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.psi.NjkStatement;
import dev.njk.psi.NjkTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Folds block statements between their opening and closing tags, and multi-line comments. */
public final class NjkFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> result = new ArrayList<>();
        for (NjkStatement statement : PsiTreeUtil.findChildrenOfType(root, NjkStatement.class)) {
            NjkTag open = statement.getOpenTag();
            NjkTag end = statement.getEndTag();
            if (open == null || end == null) continue;
            TextRange range = new TextRange(open.getTextRange().getEndOffset(), end.getTextRange().getStartOffset());
            if (spansLines(document, range)) result.add(new FoldingDescriptor(statement.getNode(), range));
        }
        PsiTreeUtil.processElements(root, element -> {
            if (element.getNode().getElementType() == NjkTokenTypes.COMMENT && spansLines(document, element.getTextRange())) {
                result.add(new FoldingDescriptor(element.getNode(), element.getTextRange()));
            }
            return true;
        });
        return result.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static boolean spansLines(Document document, TextRange range) {
        return !range.isEmpty() && document.getLineNumber(range.getStartOffset()) != document.getLineNumber(range.getEndOffset());
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return node.getElementType() == NjkTokenTypes.COMMENT ? "{# ... #}" : "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
