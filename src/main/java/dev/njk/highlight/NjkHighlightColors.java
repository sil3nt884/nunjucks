package dev.njk.highlight;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public final class NjkHighlightColors {
    public static final TextAttributesKey DELIMITER =
            createTextAttributesKey("NJK_DELIMITER", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey TAG_NAME =
            createTextAttributesKey("NJK_TAG_NAME", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("NJK_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("NJK_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey STRING =
            createTextAttributesKey("NJK_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("NJK_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey COMMENT =
            createTextAttributesKey("NJK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("NJK_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey PARENTHESES =
            createTextAttributesKey("NJK_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("NJK_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey BRACES =
            createTextAttributesKey("NJK_BRACES", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey DOT =
            createTextAttributesKey("NJK_DOT", DefaultLanguageHighlighterColors.DOT);
    public static final TextAttributesKey COMMA =
            createTextAttributesKey("NJK_COMMA", DefaultLanguageHighlighterColors.COMMA);
    public static final TextAttributesKey FRONT_MATTER =
            createTextAttributesKey("NJK_FRONT_MATTER", DefaultLanguageHighlighterColors.DOC_COMMENT);
    public static final TextAttributesKey BAD_CHARACTER =
            createTextAttributesKey("NJK_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

    // Semantic colors applied by the annotator.
    public static final TextAttributesKey FILTER =
            createTextAttributesKey("NJK_FILTER", DefaultLanguageHighlighterColors.STATIC_METHOD);
    public static final TextAttributesKey TEST =
            createTextAttributesKey("NJK_TEST", DefaultLanguageHighlighterColors.STATIC_METHOD);
    public static final TextAttributesKey FUNCTION_CALL =
            createTextAttributesKey("NJK_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey MACRO_DECLARATION =
            createTextAttributesKey("NJK_MACRO_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey VARIABLE_DECLARATION =
            createTextAttributesKey("NJK_VARIABLE_DECLARATION", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    public static final TextAttributesKey PARAMETER =
            createTextAttributesKey("NJK_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);
    public static final TextAttributesKey MEMBER =
            createTextAttributesKey("NJK_MEMBER", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey BLOCK_NAME =
            createTextAttributesKey("NJK_BLOCK_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
    public static final TextAttributesKey CUSTOM_TAG =
            createTextAttributesKey("NJK_CUSTOM_TAG", DefaultLanguageHighlighterColors.METADATA);

    private NjkHighlightColors() {
    }
}
