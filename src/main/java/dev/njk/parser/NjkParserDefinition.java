package dev.njk.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import dev.njk.NjkFile;
import dev.njk.lexer.NjkLexer;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.psi.*;
import org.jetbrains.annotations.NotNull;

import static dev.njk.parser.NjkElementTypes.*;

public final class NjkParserDefinition implements ParserDefinition {
    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new NjkLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new NjkParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return NjkTokenTypes.COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return NjkTokenTypes.STRINGS;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        IElementType type = node.getElementType();
        if (type == TAG) return new NjkTag(node);
        if (STATEMENTS.contains(type)) return new NjkStatement(node);
        if (type == REFERENCE_EXPRESSION) return new NjkReferenceExpression(node);
        if (type == MEMBER_EXPRESSION) return new NjkMemberExpression(node);
        if (type == FILTER_NAME) return new NjkFilterName(node);
        if (type == TEST_NAME) return new NjkTestName(node);
        if (type == STRING_LITERAL) return new NjkStringLiteral(node);
        if (type == VARIABLE) return new NjkVariable(node);
        if (type == IMPORTED_NAME) return new NjkImportedName(node);
        if (type == BLOCK_NAME) return new NjkBlockName(node);
        if (type == DICT_KEY) return new NjkDictKey(node);
        return new NjkPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new NjkFile(viewProvider);
    }
}
