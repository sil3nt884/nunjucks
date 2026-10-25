package dev.njk.parser;

import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import dev.njk.NjkLanguage;
import dev.njk.lexer.NjkTokenTypes;

public interface NjkElementTypes {
    IFileElementType FILE = new IFileElementType("NJK_FILE", NjkLanguage.INSTANCE);

    TemplateDataElementType TEMPLATE_DATA = new TemplateDataElementType(
            "NJK_TEMPLATE_DATA", NjkLanguage.INSTANCE, NjkTokenTypes.TEMPLATE_TEXT, NjkTokenTypes.OUTER_ELEMENT_TYPE);

    /** {@code {{ expression }}} */
    IElementType OUTPUT = new NjkElementType("OUTPUT");
    /** Any {@code {% ... %}} tag. */
    IElementType TAG = new NjkElementType("TAG");

    // A tag together with its body and closing tag.
    IElementType IF_STATEMENT = new NjkElementType("IF_STATEMENT");
    IElementType FOR_STATEMENT = new NjkElementType("FOR_STATEMENT");
    IElementType SET_STATEMENT = new NjkElementType("SET_STATEMENT");
    IElementType BLOCK_STATEMENT = new NjkElementType("BLOCK_STATEMENT");
    IElementType MACRO_STATEMENT = new NjkElementType("MACRO_STATEMENT");
    IElementType CALL_STATEMENT = new NjkElementType("CALL_STATEMENT");
    IElementType FILTER_STATEMENT = new NjkElementType("FILTER_STATEMENT");
    IElementType RAW_STATEMENT = new NjkElementType("RAW_STATEMENT");
    IElementType CUSTOM_STATEMENT = new NjkElementType("CUSTOM_STATEMENT");

    TokenSet STATEMENTS = TokenSet.create(IF_STATEMENT, FOR_STATEMENT, SET_STATEMENT, BLOCK_STATEMENT,
            MACRO_STATEMENT, CALL_STATEMENT, FILTER_STATEMENT, RAW_STATEMENT, CUSTOM_STATEMENT);
    /** Statements whose variables are not visible after them. */
    TokenSet SCOPING_STATEMENTS = TokenSet.create(FOR_STATEMENT, BLOCK_STATEMENT, MACRO_STATEMENT, CALL_STATEMENT);

    IElementType REFERENCE_EXPRESSION = new NjkElementType("REFERENCE_EXPRESSION");
    IElementType MEMBER_EXPRESSION = new NjkElementType("MEMBER_EXPRESSION");
    IElementType INDEX_EXPRESSION = new NjkElementType("INDEX_EXPRESSION");
    IElementType CALL_EXPRESSION = new NjkElementType("CALL_EXPRESSION");
    IElementType ARGUMENT_LIST = new NjkElementType("ARGUMENT_LIST");
    IElementType KEYWORD_ARGUMENT = new NjkElementType("KEYWORD_ARGUMENT");
    IElementType FILTER_EXPRESSION = new NjkElementType("FILTER_EXPRESSION");
    IElementType FILTER_NAME = new NjkElementType("FILTER_NAME");
    IElementType TEST_EXPRESSION = new NjkElementType("TEST_EXPRESSION");
    IElementType TEST_NAME = new NjkElementType("TEST_NAME");
    IElementType BINARY_EXPRESSION = new NjkElementType("BINARY_EXPRESSION");
    IElementType UNARY_EXPRESSION = new NjkElementType("UNARY_EXPRESSION");
    IElementType CONDITIONAL_EXPRESSION = new NjkElementType("CONDITIONAL_EXPRESSION");
    IElementType LITERAL = new NjkElementType("LITERAL");
    IElementType STRING_LITERAL = new NjkElementType("STRING_LITERAL");
    IElementType LIST_LITERAL = new NjkElementType("LIST_LITERAL");
    IElementType DICT_LITERAL = new NjkElementType("DICT_LITERAL");
    IElementType DICT_ENTRY = new NjkElementType("DICT_ENTRY");
    IElementType DICT_KEY = new NjkElementType("DICT_KEY");
    IElementType PARENTHESIZED_EXPRESSION = new NjkElementType("PARENTHESIZED_EXPRESSION");
    IElementType TUPLE_EXPRESSION = new NjkElementType("TUPLE_EXPRESSION");

    /** A name introduced by set, for, macro, a macro parameter or an import alias. */
    IElementType VARIABLE = new NjkElementType("VARIABLE");
    IElementType PARAMETER_LIST = new NjkElementType("PARAMETER_LIST");
    IElementType PARAMETER = new NjkElementType("PARAMETER");
    IElementType IMPORT_SPECIFIER = new NjkElementType("IMPORT_SPECIFIER");
    IElementType IMPORTED_NAME = new NjkElementType("IMPORTED_NAME");
    IElementType BLOCK_NAME = new NjkElementType("BLOCK_NAME");
}
