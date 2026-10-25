package dev.njk.highlight;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import dev.njk.lexer.NjkLexer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static dev.njk.lexer.NjkTokenTypes.*;

public final class NjkSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey> KEYS = new HashMap<>();

    static {
        for (IElementType t : DELIMITERS.getTypes()) KEYS.put(t, NjkHighlightColors.DELIMITER);
        for (IElementType t : KEYWORDS.getTypes()) KEYS.put(t, NjkHighlightColors.KEYWORD);
        for (IElementType t : OPERATORS.getTypes()) KEYS.put(t, NjkHighlightColors.OPERATOR);
        KEYS.put(TAG_NAME, NjkHighlightColors.TAG_NAME);
        KEYS.put(IDENTIFIER, NjkHighlightColors.IDENTIFIER);
        KEYS.put(STRING, NjkHighlightColors.STRING);
        KEYS.put(NUMBER, NjkHighlightColors.NUMBER);
        KEYS.put(COMMENT, NjkHighlightColors.COMMENT);
        KEYS.put(FRONT_MATTER, NjkHighlightColors.FRONT_MATTER);
        KEYS.put(LPAREN, NjkHighlightColors.PARENTHESES);
        KEYS.put(RPAREN, NjkHighlightColors.PARENTHESES);
        KEYS.put(LBRACKET, NjkHighlightColors.BRACKETS);
        KEYS.put(RBRACKET, NjkHighlightColors.BRACKETS);
        KEYS.put(LBRACE, NjkHighlightColors.BRACES);
        KEYS.put(RBRACE, NjkHighlightColors.BRACES);
        KEYS.put(DOT, NjkHighlightColors.DOT);
        KEYS.put(COMMA, NjkHighlightColors.COMMA);
        KEYS.put(BAD_CHARACTER, NjkHighlightColors.BAD_CHARACTER);
    }

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new NjkLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return pack(KEYS.get(tokenType));
    }
}
