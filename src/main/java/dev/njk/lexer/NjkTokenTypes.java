package dev.njk.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.OuterLanguageElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import dev.njk.NjkLanguage;

public interface NjkTokenTypes {
    /** Text outside of Nunjucks tags; parsed by the template data language (HTML by default). */
    IElementType TEMPLATE_TEXT = new NjkTokenType("TEMPLATE_TEXT");
    /** Nunjucks code as seen from the template data language tree. */
    IElementType OUTER_ELEMENT_TYPE = new OuterLanguageElementType("NJK_FRAGMENT", NjkLanguage.INSTANCE);

    IElementType FRONT_MATTER = new NjkTokenType("FRONT_MATTER");
    IElementType COMMENT = new NjkTokenType("COMMENT");

    IElementType BLOCK_START = new NjkTokenType("{%");
    IElementType BLOCK_END = new NjkTokenType("%}");
    IElementType VAR_START = new NjkTokenType("{{");
    IElementType VAR_END = new NjkTokenType("}}");

    IElementType TAG_NAME = new NjkTokenType("TAG_NAME");
    IElementType IDENTIFIER = new NjkTokenType("IDENTIFIER");
    IElementType STRING = new NjkTokenType("STRING");
    IElementType NUMBER = new NjkTokenType("NUMBER");
    /** {@code r/[a-z]+/gi} */
    IElementType REGEX = new NjkTokenType("REGEX");

    IElementType KW_AND = new NjkTokenType("and");
    IElementType KW_OR = new NjkTokenType("or");
    IElementType KW_NOT = new NjkTokenType("not");
    IElementType KW_IN = new NjkTokenType("in");
    IElementType KW_IS = new NjkTokenType("is");
    IElementType KW_IF = new NjkTokenType("if");
    IElementType KW_ELSE = new NjkTokenType("else");
    IElementType KW_TRUE = new NjkTokenType("true");
    IElementType KW_FALSE = new NjkTokenType("false");
    IElementType KW_NONE = new NjkTokenType("none");
    // Only keywords inside {% %}; usable as plain names in expressions.
    IElementType KW_AS = new NjkTokenType("as");
    IElementType KW_IMPORT = new NjkTokenType("import");
    IElementType KW_WITH = new NjkTokenType("with");
    IElementType KW_WITHOUT = new NjkTokenType("without");
    IElementType KW_CONTEXT = new NjkTokenType("context");
    IElementType KW_IGNORE = new NjkTokenType("ignore");
    IElementType KW_MISSING = new NjkTokenType("missing");

    IElementType DOT = new NjkTokenType(".");
    IElementType COMMA = new NjkTokenType(",");
    IElementType COLON = new NjkTokenType(":");
    IElementType PIPE = new NjkTokenType("|");
    IElementType LPAREN = new NjkTokenType("(");
    IElementType RPAREN = new NjkTokenType(")");
    IElementType LBRACKET = new NjkTokenType("[");
    IElementType RBRACKET = new NjkTokenType("]");
    IElementType LBRACE = new NjkTokenType("{");
    IElementType RBRACE = new NjkTokenType("}");
    IElementType ASSIGN = new NjkTokenType("=");
    IElementType EQEQ = new NjkTokenType("==");
    IElementType NEQ = new NjkTokenType("!=");
    IElementType LT = new NjkTokenType("<");
    IElementType GT = new NjkTokenType(">");
    IElementType LE = new NjkTokenType("<=");
    IElementType GE = new NjkTokenType(">=");
    IElementType PLUS = new NjkTokenType("+");
    IElementType MINUS = new NjkTokenType("-");
    IElementType MUL = new NjkTokenType("*");
    IElementType DIV = new NjkTokenType("/");
    IElementType FLOORDIV = new NjkTokenType("//");
    IElementType MOD = new NjkTokenType("%");
    IElementType POW = new NjkTokenType("**");
    IElementType TILDE = new NjkTokenType("~");

    IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

    TokenSet COMMENTS = TokenSet.create(COMMENT);
    TokenSet STRINGS = TokenSet.create(STRING);
    TokenSet DELIMITERS = TokenSet.create(BLOCK_START, BLOCK_END, VAR_START, VAR_END);
    TokenSet SOFT_KEYWORDS = TokenSet.create(KW_AS, KW_IMPORT, KW_WITH, KW_WITHOUT, KW_CONTEXT, KW_IGNORE, KW_MISSING);
    TokenSet KEYWORDS = TokenSet.orSet(SOFT_KEYWORDS, TokenSet.create(
            KW_AND, KW_OR, KW_NOT, KW_IN, KW_IS, KW_IF, KW_ELSE, KW_TRUE, KW_FALSE, KW_NONE));
    TokenSet OPERATORS = TokenSet.create(PIPE, ASSIGN, EQEQ, NEQ, LT, GT, LE, GE, PLUS, MINUS, MUL, DIV,
            FLOORDIV, MOD, POW, TILDE, COLON);
}
