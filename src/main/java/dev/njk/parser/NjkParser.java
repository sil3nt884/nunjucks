package dev.njk.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static dev.njk.lexer.NjkTokenTypes.*;
import static dev.njk.parser.NjkElementTypes.*;

/**
 * Recursive descent parser producing the Nunjucks PSI tree. Template text stays as plain leaves;
 * block tags are grouped with their bodies and closing tags into statement elements.
 */
public final class NjkParser implements PsiParser {

    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker file = builder.mark();
        new Impl(builder).parseContent(Set.of());
        while (!builder.eof()) builder.advanceLexer();
        file.done(root);
        return builder.getTreeBuilt();
    }

    private static final TokenSet OR_OPS = TokenSet.create(KW_OR);
    private static final TokenSet AND_OPS = TokenSet.create(KW_AND);
    private static final TokenSet COMPARE_OPS = TokenSet.create(EQEQ, NEQ, LT, GT, LE, GE);
    private static final TokenSet CONCAT_OPS = TokenSet.create(TILDE);
    private static final TokenSet ADD_OPS = TokenSet.create(PLUS, MINUS);
    private static final TokenSet MUL_OPS = TokenSet.create(MUL, DIV, FLOORDIV, MOD);
    private static final TokenSet POW_OPS = TokenSet.create(POW);
    private static final TokenSet BOUNDARY = TokenSet.create(BLOCK_END, VAR_END, BLOCK_START, VAR_START, TEMPLATE_TEXT);

    private static final class Impl {
        private final PsiBuilder b;
        /** Custom tags known to have no closing tag in the rest of the file. */
        private final Set<String> tagsWithoutEnd = new HashSet<>();

        Impl(PsiBuilder builder) {
            this.b = builder;
        }

        // ---------------------------------------------------------------- template structure

        void parseContent(Set<String> stops) {
            while (!b.eof()) {
                IElementType t = b.getTokenType();
                if (t == BLOCK_START) {
                    String name = peekTagName();
                    if (name != null && stops.contains(name)) return;
                    parseStatement(name, stops);
                } else if (t == VAR_START) {
                    parseOutput();
                } else {
                    b.advanceLexer();
                }
            }
        }

        private @Nullable String peekTagName() {
            if (b.getTokenType() != BLOCK_START || b.lookAhead(1) != TAG_NAME) return null;
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            String name = b.getTokenText();
            m.rollbackTo();
            return name;
        }

        private void parseStatement(@Nullable String name, Set<String> stops) {
            if (name == null) {
                parseTag(null);
                return;
            }
            NjkTagSpec spec = NjkTagSpec.block(name);
            if (name.equals("set") && !setHasAssignment()) spec = NjkTagSpec.SET_BLOCK;
            if (spec == null) {
                if (NjkTagSpec.isEndOrContinuation(name)) {
                    PsiBuilder.Marker err = b.mark();
                    parseTag(name);
                    err.error("Unexpected {% " + name + " %}");
                    return;
                }
                if (NjkTagSpec.SIMPLE_TAGS.contains(name) || !hasCustomEnd(name, stops)) {
                    parseTag(name);
                    return;
                }
                spec = new NjkTagSpec(name, "end" + name, Set.of(), CUSTOM_STATEMENT);
            }

            PsiBuilder.Marker statement = b.mark();
            parseTag(name);
            Set<String> inner = new HashSet<>(stops);
            inner.addAll(spec.continuations());
            inner.add(spec.end());
            while (true) {
                parseContent(inner);
                String next = peekTagName();
                if (next != null && spec.continuations().contains(next)) {
                    parseTag(next);
                    continue;
                }
                if (next != null && next.equals(spec.end())) {
                    parseTag(next);
                } else {
                    b.error("Missing {% " + spec.end() + " %}");
                }
                break;
            }
            statement.done(spec.statementType());
        }

        private boolean setHasAssignment() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            boolean found = false;
            while (!b.eof()) {
                IElementType t = b.getTokenType();
                if (t == BLOCK_END || t == BLOCK_START || t == VAR_START) break;
                if (t == ASSIGN) {
                    found = true;
                    break;
                }
                b.advanceLexer();
            }
            m.rollbackTo();
            return found;
        }

        /** Looks ahead for {@code {% endNAME %}} to decide whether a custom tag (e.g. a paired shortcode) has a body. */
        private boolean hasCustomEnd(String name, Set<String> stops) {
            if (tagsWithoutEnd.contains(name)) return false;
            String end = "end" + name;
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            b.advanceLexer();
            int depth = 0;
            boolean found = false;
            boolean stopped = false;
            while (!b.eof()) {
                if (b.getTokenType() == TAG_NAME) {
                    String t = b.getTokenText();
                    if (name.equals(t)) {
                        depth++;
                    } else if (end.equals(t)) {
                        if (depth == 0) {
                            found = true;
                            break;
                        }
                        depth--;
                    } else if (t != null && stops.contains(t)) {
                        stopped = true;
                        break;
                    }
                }
                b.advanceLexer();
            }
            m.rollbackTo();
            if (!found && !stopped) tagsWithoutEnd.add(name);
            return found;
        }

        private void parseTag(@Nullable String name) {
            PsiBuilder.Marker tag = b.mark();
            b.advanceLexer();
            if (at(TAG_NAME)) b.advanceLexer();
            if (name != null) parseTagBody(name);
            finish(BLOCK_END, "%} expected");
            tag.done(TAG);
        }

        private void parseTagBody(String name) {
            switch (name) {
                case "if", "elif", "elseif", "ifAsync" -> requireExpression();
                case "for", "asyncEach", "asyncAll" -> {
                    boolean paren = at(LPAREN);
                    if (paren) b.advanceLexer();
                    variable();
                    while (at(COMMA)) {
                        b.advanceLexer();
                        variable();
                    }
                    if (paren) expect(RPAREN, "')' expected");
                    if (expect(KW_IN, "'in' expected")) requireExpression();
                }
                case "set" -> {
                    variable();
                    while (at(COMMA)) {
                        b.advanceLexer();
                        variable();
                    }
                    if (at(ASSIGN)) {
                        b.advanceLexer();
                        requireExpression();
                    }
                }
                case "block", "endblock" -> {
                    if (at(IDENTIFIER)) {
                        PsiBuilder.Marker m = b.mark();
                        b.advanceLexer();
                        m.done(BLOCK_NAME);
                    } else if (name.equals("block")) {
                        b.error("Block name expected");
                    }
                }
                case "macro" -> {
                    variable();
                    if (at(LPAREN)) parameterList();
                }
                case "call" -> {
                    if (at(LPAREN)) parameterList();
                    requireExpression();
                }
                case "filter" -> {
                    if (at(IDENTIFIER)) {
                        PsiBuilder.Marker m = b.mark();
                        b.advanceLexer();
                        m.done(FILTER_NAME);
                        if (at(LPAREN)) argumentList();
                    } else {
                        b.error("Filter name expected");
                    }
                }
                case "extends" -> requireExpression();
                case "include" -> {
                    requireExpression();
                    contextModifiers();
                }
                case "import" -> {
                    requireExpression();
                    if (expect(KW_AS, "'as' expected")) variable();
                    contextModifiers();
                }
                case "from" -> {
                    requireExpression();
                    if (expect(KW_IMPORT, "'import' expected")) {
                        importSpecifier();
                        while (at(COMMA)) {
                            b.advanceLexer();
                            importSpecifier();
                        }
                    }
                    contextModifiers();
                }
                default -> {
                    if (!NjkTagSpec.isBuiltin(name)) looseArguments();
                }
            }
        }

        private void contextModifiers() {
            while (true) {
                if (at(KW_IGNORE)) {
                    b.advanceLexer();
                    expect(KW_MISSING, "'missing' expected");
                } else if (at(KW_WITH) || at(KW_WITHOUT)) {
                    b.advanceLexer();
                    expect(KW_CONTEXT, "'context' expected");
                } else {
                    return;
                }
            }
        }

        private void importSpecifier() {
            if (!at(IDENTIFIER)) {
                b.error("Name expected");
                return;
            }
            PsiBuilder.Marker spec = b.mark();
            PsiBuilder.Marker name = b.mark();
            b.advanceLexer();
            name.done(IMPORTED_NAME);
            if (at(KW_AS)) {
                b.advanceLexer();
                variable();
            }
            spec.done(IMPORT_SPECIFIER);
        }

        /** Arguments of custom tags and shortcodes: {@code {% image "a.png", alt="x" %}}. */
        private void looseArguments() {
            while (!atBoundary()) {
                if (at(COMMA)) {
                    b.advanceLexer();
                } else if (at(IDENTIFIER) && b.lookAhead(1) == ASSIGN) {
                    keywordArgument();
                } else if (!parseExpression()) {
                    return;
                }
            }
        }

        private void variable() {
            if (at(IDENTIFIER)) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                m.done(VARIABLE);
            } else {
                b.error("Identifier expected");
            }
        }

        private void parameterList() {
            PsiBuilder.Marker list = b.mark();
            b.advanceLexer();
            while (!at(RPAREN) && !atBoundary()) {
                if (!at(IDENTIFIER)) {
                    b.error("Parameter name expected");
                    break;
                }
                PsiBuilder.Marker param = b.mark();
                variable();
                if (at(ASSIGN)) {
                    b.advanceLexer();
                    requireExpression();
                }
                param.done(PARAMETER);
                if (!at(COMMA)) break;
                b.advanceLexer();
            }
            expect(RPAREN, "')' expected");
            list.done(PARAMETER_LIST);
        }

        private void parseOutput() {
            PsiBuilder.Marker output = b.mark();
            b.advanceLexer();
            if (!atBoundary()) requireExpression();
            finish(VAR_END, "}} expected");
            output.done(OUTPUT);
        }

        /** Consumes the closing delimiter, wrapping any unparsed tokens before it in an error. */
        private void finish(IElementType end, String message) {
            if (at(end)) {
                b.advanceLexer();
                return;
            }
            if (!atBoundary()) {
                PsiBuilder.Marker err = b.mark();
                while (!b.eof() && !atBoundary()) b.advanceLexer();
                err.error("Unexpected token");
                if (at(end)) {
                    b.advanceLexer();
                    return;
                }
            }
            b.error(message);
        }

        // ---------------------------------------------------------------- expressions

        boolean parseExpression() {
            PsiBuilder.Marker m = b.mark();
            if (!parseBinary(this::parseAnd, OR_OPS)) {
                m.drop();
                return false;
            }
            if (at(KW_IF)) {
                b.advanceLexer();
                if (!parseBinary(this::parseAnd, OR_OPS)) b.error("Expression expected");
                if (at(KW_ELSE)) {
                    b.advanceLexer();
                    requireExpression();
                }
                m.done(CONDITIONAL_EXPRESSION);
            } else {
                m.drop();
            }
            return true;
        }

        private void requireExpression() {
            if (!parseExpression()) b.error("Expression expected");
        }

        private boolean parseBinary(BooleanSupplier operand, TokenSet ops) {
            PsiBuilder.Marker m = b.mark();
            if (!operand.getAsBoolean()) {
                m.drop();
                return false;
            }
            while (ops.contains(b.getTokenType())) {
                b.advanceLexer();
                if (!operand.getAsBoolean()) b.error("Expression expected");
                m.done(BINARY_EXPRESSION);
                m = m.precede();
            }
            m.drop();
            return true;
        }

        private boolean parseAnd() {
            return parseBinary(this::parseNot, AND_OPS);
        }

        private boolean parseNot() {
            if (at(KW_NOT)) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                if (!parseNot()) b.error("Expression expected");
                m.done(UNARY_EXPRESSION);
                return true;
            }
            return parseIn();
        }

        private boolean parseIn() {
            PsiBuilder.Marker m = b.mark();
            if (!parseIs()) {
                m.drop();
                return false;
            }
            while (true) {
                if (at(KW_IN)) {
                    b.advanceLexer();
                } else if (at(KW_NOT) && b.lookAhead(1) == KW_IN) {
                    b.advanceLexer();
                    b.advanceLexer();
                } else {
                    break;
                }
                if (!parseIs()) b.error("Expression expected");
                m.done(BINARY_EXPRESSION);
                m = m.precede();
            }
            m.drop();
            return true;
        }

        private boolean parseIs() {
            PsiBuilder.Marker m = b.mark();
            if (!parseBinary(this::parseConcat, COMPARE_OPS)) {
                m.drop();
                return false;
            }
            if (!at(KW_IS)) {
                m.drop();
                return true;
            }
            b.advanceLexer();
            if (at(KW_NOT)) b.advanceLexer();
            if (at(IDENTIFIER) || KEYWORDS.contains(b.getTokenType())) {
                b.remapCurrentToken(IDENTIFIER);
                PsiBuilder.Marker test = b.mark();
                b.advanceLexer();
                test.done(TEST_NAME);
                if (at(LPAREN)) argumentList();
            } else {
                b.error("Test name expected");
            }
            m.done(TEST_EXPRESSION);
            return true;
        }

        private boolean parseConcat() {
            return parseBinary(this::parseAdd, CONCAT_OPS);
        }

        private boolean parseAdd() {
            return parseBinary(this::parseMul, ADD_OPS);
        }

        private boolean parseMul() {
            return parseBinary(this::parsePow, MUL_OPS);
        }

        private boolean parsePow() {
            return parseBinary(this::parseUnary, POW_OPS);
        }

        private boolean parseUnary() {
            if (at(MINUS) || at(PLUS)) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                if (!parseUnary()) b.error("Expression expected");
                m.done(UNARY_EXPRESSION);
                return true;
            }
            return parseFiltered();
        }

        private boolean parseFiltered() {
            PsiBuilder.Marker m = b.mark();
            if (!parsePostfix()) {
                m.drop();
                return false;
            }
            while (at(PIPE)) {
                b.advanceLexer();
                if (at(IDENTIFIER)) {
                    PsiBuilder.Marker name = b.mark();
                    b.advanceLexer();
                    name.done(FILTER_NAME);
                    if (at(LPAREN)) argumentList();
                } else {
                    b.error("Filter name expected");
                }
                m.done(FILTER_EXPRESSION);
                m = m.precede();
            }
            m.drop();
            return true;
        }

        private boolean parsePostfix() {
            PsiBuilder.Marker m = b.mark();
            if (!parsePrimary()) {
                m.drop();
                return false;
            }
            while (true) {
                if (at(DOT)) {
                    b.advanceLexer();
                    if (at(IDENTIFIER) || KEYWORDS.contains(b.getTokenType())) {
                        b.remapCurrentToken(IDENTIFIER);
                        b.advanceLexer();
                    } else {
                        b.error("Name expected");
                    }
                    m.done(MEMBER_EXPRESSION);
                } else if (at(LBRACKET)) {
                    b.advanceLexer();
                    requireExpression();
                    expect(RBRACKET, "']' expected");
                    m.done(INDEX_EXPRESSION);
                } else if (at(LPAREN)) {
                    argumentList();
                    m.done(CALL_EXPRESSION);
                } else {
                    break;
                }
                m = m.precede();
            }
            m.drop();
            return true;
        }

        private boolean parsePrimary() {
            IElementType t = b.getTokenType();
            if (t == IDENTIFIER || SOFT_KEYWORDS.contains(t)) {
                b.remapCurrentToken(IDENTIFIER);
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                m.done(REFERENCE_EXPRESSION);
                return true;
            }
            if (t == STRING) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                m.done(STRING_LITERAL);
                return true;
            }
            if (t == NUMBER || t == KW_TRUE || t == KW_FALSE || t == KW_NONE) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                m.done(LITERAL);
                return true;
            }
            if (t == LPAREN) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                boolean tuple = false;
                if (at(RPAREN)) {
                    tuple = true;
                } else {
                    requireExpression();
                    while (at(COMMA)) {
                        tuple = true;
                        b.advanceLexer();
                        if (at(RPAREN)) break;
                        requireExpression();
                    }
                }
                expect(RPAREN, "')' expected");
                m.done(tuple ? TUPLE_EXPRESSION : PARENTHESIZED_EXPRESSION);
                return true;
            }
            if (t == LBRACKET) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                while (!at(RBRACKET) && !atBoundary()) {
                    if (!parseExpression()) {
                        b.error("Expression expected");
                        break;
                    }
                    if (!at(COMMA)) break;
                    b.advanceLexer();
                }
                expect(RBRACKET, "']' expected");
                m.done(LIST_LITERAL);
                return true;
            }
            if (t == LBRACE) {
                parseDict();
                return true;
            }
            return false;
        }

        private void parseDict() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            while (!at(RBRACE) && !atBoundary()) {
                PsiBuilder.Marker entry = b.mark();
                IElementType t = b.getTokenType();
                if (t == IDENTIFIER || t == STRING || KEYWORDS.contains(t)) {
                    if (t != STRING) b.remapCurrentToken(IDENTIFIER);
                    PsiBuilder.Marker key = b.mark();
                    b.advanceLexer();
                    key.done(DICT_KEY);
                } else if (!parseExpression()) {
                    entry.drop();
                    b.error("Key expected");
                    break;
                }
                if (expect(COLON, "':' expected")) requireExpression();
                entry.done(DICT_ENTRY);
                if (!at(COMMA)) break;
                b.advanceLexer();
            }
            expect(RBRACE, "'}' expected");
            m.done(DICT_LITERAL);
        }

        private void argumentList() {
            PsiBuilder.Marker list = b.mark();
            b.advanceLexer();
            while (!at(RPAREN) && !atBoundary()) {
                if (at(IDENTIFIER) && b.lookAhead(1) == ASSIGN) {
                    keywordArgument();
                } else if (!parseExpression()) {
                    b.error("Expression expected");
                    break;
                }
                if (!at(COMMA)) break;
                b.advanceLexer();
            }
            expect(RPAREN, "')' expected");
            list.done(ARGUMENT_LIST);
        }

        private void keywordArgument() {
            PsiBuilder.Marker arg = b.mark();
            b.advanceLexer();
            b.advanceLexer();
            requireExpression();
            arg.done(KEYWORD_ARGUMENT);
        }

        // ---------------------------------------------------------------- helpers

        private boolean at(IElementType type) {
            return b.getTokenType() == type;
        }

        private boolean atBoundary() {
            return b.eof() || BOUNDARY.contains(b.getTokenType());
        }

        private boolean expect(IElementType type, String message) {
            if (at(type)) {
                b.advanceLexer();
                return true;
            }
            b.error(message);
            return false;
        }
    }
}
