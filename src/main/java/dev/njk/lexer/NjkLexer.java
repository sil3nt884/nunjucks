package dev.njk.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static dev.njk.lexer.NjkTokenTypes.*;

/**
 * Hand-written lexer for Nunjucks templates.
 * <p>
 * The state packs the current mode in the low 4 bits and the depth of open {@code {}} braces
 * (so that {@code }}} inside a dict literal does not close an output tag) in the next 4 bits.
 */
public final class NjkLexer extends LexerBase {
    static final int TEXT = 0;
    static final int BLOCK_NAME = 1;
    static final int BLOCK = 2;
    static final int VAR = 3;
    static final int RAW_TAG = 4;
    static final int RAW_BODY = 5;
    static final int VERBATIM_TAG = 6;
    static final int VERBATIM_BODY = 7;

    private static final Map<String, IElementType> EXPRESSION_KEYWORDS = Map.ofEntries(
            Map.entry("and", KW_AND), Map.entry("or", KW_OR), Map.entry("not", KW_NOT),
            Map.entry("in", KW_IN), Map.entry("is", KW_IS), Map.entry("if", KW_IF), Map.entry("else", KW_ELSE),
            Map.entry("true", KW_TRUE), Map.entry("True", KW_TRUE),
            Map.entry("false", KW_FALSE), Map.entry("False", KW_FALSE),
            Map.entry("none", KW_NONE), Map.entry("None", KW_NONE), Map.entry("null", KW_NONE));

    private static final Map<String, IElementType> TAG_KEYWORDS = Map.of(
            "as", KW_AS, "import", KW_IMPORT, "with", KW_WITH, "without", KW_WITHOUT,
            "context", KW_CONTEXT, "ignore", KW_IGNORE, "missing", KW_MISSING);

    private static final String[] LONG_OPERATORS = {"===", "!==", "==", "!=", "<=", ">=", "**", "//"};

    private CharSequence buffer = "";
    private int bufferEnd;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;
    private int state;
    private int nextState;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.tokenEnd = startOffset;
        this.nextState = initialState;
        advance();
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }

    @Override
    public void advance() {
        tokenStart = tokenEnd;
        state = nextState;
        if (tokenStart >= bufferEnd) {
            tokenType = null;
            return;
        }
        int mode = state & 0xF;
        int depth = (state >> 4) & 0xF;
        switch (mode) {
            case TEXT -> lexText(tokenStart);
            case RAW_BODY -> lexRawBody(tokenStart, "endraw");
            case VERBATIM_BODY -> lexRawBody(tokenStart, "endverbatim");
            default -> lexCode(tokenStart, mode, depth);
        }
    }

    private void token(IElementType type, int end, int mode, int depth) {
        tokenType = type;
        tokenEnd = Math.min(end, bufferEnd);
        nextState = mode | (Math.max(0, Math.min(depth, 15)) << 4);
    }

    private char at(int i) {
        return i < bufferEnd ? buffer.charAt(i) : '\0';
    }

    private boolean startsWith(int pos, String s) {
        if (pos + s.length() > bufferEnd) return false;
        for (int i = 0; i < s.length(); i++) {
            if (buffer.charAt(pos + i) != s.charAt(i)) return false;
        }
        return true;
    }

    private int indexOf(String s, int from) {
        for (int i = from; i + s.length() <= bufferEnd; i++) {
            if (startsWith(i, s)) return i;
        }
        return -1;
    }

    private boolean isLineEnd(int i) {
        return i >= bufferEnd || buffer.charAt(i) == '\n' || buffer.charAt(i) == '\r';
    }

    private boolean isTagStart(int pos) {
        if (at(pos) != '{') return false;
        char n = at(pos + 1);
        return n == '{' || n == '%' || n == '#';
    }

    private void lexText(int pos) {
        if (pos == 0 && startsWith(0, "---") && isLineEnd(3)) {
            int close = findFrontMatterEnd();
            if (close > 0) {
                token(FRONT_MATTER, close, TEXT, 0);
                return;
            }
        }
        if (startsWith(pos, "{{")) {
            int e = pos + 2;
            if (at(e) == '-') e++;
            token(VAR_START, e, VAR, 0);
            return;
        }
        if (startsWith(pos, "{%")) {
            int e = pos + 2;
            if (at(e) == '-') e++;
            token(BLOCK_START, e, BLOCK_NAME, 0);
            return;
        }
        if (startsWith(pos, "{#")) {
            int close = indexOf("#}", pos + 2);
            token(COMMENT, close < 0 ? bufferEnd : close + 2, TEXT, 0);
            return;
        }
        int e = pos;
        while (e < bufferEnd && !isTagStart(e)) e++;
        token(TEMPLATE_TEXT, e, TEXT, 0);
    }

    /** Returns the offset after the closing {@code ---} of a front matter block, or -1. */
    private int findFrontMatterEnd() {
        int i = 3;
        while (true) {
            int nl = indexOf("\n---", i);
            if (nl < 0) return -1;
            if (isLineEnd(nl + 4)) return nl + 4;
            i = nl + 1;
        }
    }

    private void lexRawBody(int pos, String endTag) {
        int i = pos;
        while (true) {
            i = indexOf("{%", i);
            if (i < 0) {
                i = bufferEnd;
                break;
            }
            int j = i + 2;
            if (at(j) == '-') j++;
            while (j < bufferEnd && Character.isWhitespace(at(j))) j++;
            if (startsWith(j, endTag) && !isIdentPart(at(j + endTag.length()))) break;
            i += 2;
        }
        if (i > pos) {
            token(TEMPLATE_TEXT, i, TEXT, 0);
        } else {
            lexText(pos);
        }
    }

    private void lexCode(int pos, int mode, int depth) {
        char c = at(pos);
        if (Character.isWhitespace(c)) {
            int e = pos;
            while (e < bufferEnd && Character.isWhitespace(at(e))) e++;
            token(TokenType.WHITE_SPACE, e, mode, depth);
            return;
        }
        // An unterminated tag: let a new tag start take over so the rest of the file stays sane.
        if (isTagStart(pos)) {
            lexText(pos);
            return;
        }
        boolean blockMode = mode != VAR;
        if (blockMode) {
            int e = startsWith(pos, "%}") ? pos + 2 : startsWith(pos, "-%}") ? pos + 3 : -1;
            if (e > 0) {
                int after = mode == RAW_TAG ? RAW_BODY : mode == VERBATIM_TAG ? VERBATIM_BODY : TEXT;
                token(BLOCK_END, e, after, 0);
                return;
            }
        } else if (depth == 0) {
            int e = startsWith(pos, "}}") ? pos + 2 : startsWith(pos, "-}}") ? pos + 3 : -1;
            if (e > 0) {
                token(VAR_END, e, TEXT, 0);
                return;
            }
        }

        if (isIdentStart(c)) {
            int e = pos + 1;
            while (e < bufferEnd && isIdentPart(at(e))) e++;
            String word = buffer.subSequence(pos, e).toString();
            if (mode == BLOCK_NAME) {
                int next = switch (word) {
                    case "raw" -> RAW_TAG;
                    case "verbatim" -> VERBATIM_TAG;
                    default -> BLOCK;
                };
                token(TAG_NAME, e, next, depth);
                return;
            }
            IElementType kw = EXPRESSION_KEYWORDS.get(word);
            if (kw == null && blockMode) kw = TAG_KEYWORDS.get(word);
            token(kw != null ? kw : IDENTIFIER, e, mode, depth);
            return;
        }

        int m = mode == BLOCK_NAME ? BLOCK : mode;
        if (c == '"' || c == '\'') {
            int e = pos + 1;
            while (e < bufferEnd) {
                char ch = at(e);
                if (ch == '\\') {
                    e += 2;
                    continue;
                }
                if (ch == c) {
                    e++;
                    break;
                }
                if (ch == '\n') break;
                e++;
            }
            token(STRING, e, m, depth);
            return;
        }
        if (Character.isDigit(c)) {
            int e = pos;
            while (Character.isDigit(at(e))) e++;
            if (at(e) == '.' && Character.isDigit(at(e + 1))) {
                e++;
                while (Character.isDigit(at(e))) e++;
            }
            token(NUMBER, e, m, depth);
            return;
        }
        for (String op : LONG_OPERATORS) {
            if (startsWith(pos, op)) {
                IElementType t = switch (op) {
                    case "===", "==" -> EQEQ;
                    case "!==", "!=" -> NEQ;
                    case "<=" -> LE;
                    case ">=" -> GE;
                    case "**" -> POW;
                    default -> FLOORDIV;
                };
                token(t, pos + op.length(), m, depth);
                return;
            }
        }
        IElementType t = switch (c) {
            case '.' -> DOT;
            case ',' -> COMMA;
            case ':' -> COLON;
            case '|' -> PIPE;
            case '(' -> LPAREN;
            case ')' -> RPAREN;
            case '[' -> LBRACKET;
            case ']' -> RBRACKET;
            case '{' -> LBRACE;
            case '}' -> RBRACE;
            case '=' -> ASSIGN;
            case '<' -> LT;
            case '>' -> GT;
            case '+' -> PLUS;
            case '-' -> MINUS;
            case '*' -> MUL;
            case '/' -> DIV;
            case '%' -> MOD;
            case '~' -> TILDE;
            default -> BAD_CHARACTER;
        };
        int newDepth = t == LBRACE ? depth + 1 : t == RBRACE ? depth - 1 : depth;
        token(t, pos + 1, m, newDepth);
    }

    static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
