package dev.njk;

import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.njk.lexer.NjkLexer;

public class NjkLexerTest extends BasePlatformTestCase {

    private static String lex(String text) {
        NjkLexer lexer = new NjkLexer();
        lexer.start(text);
        StringBuilder sb = new StringBuilder();
        IElementType type;
        while ((type = lexer.getTokenType()) != null) {
            String name = type.toString().replace("NjkTokenType.", "");
            if (!name.equals("WHITE_SPACE")) {
                sb.append(name).append('(').append(text, lexer.getTokenStart(), lexer.getTokenEnd()).append(") ");
            }
            lexer.advance();
        }
        return sb.toString().trim();
    }

    public void testOutputWithFilter() {
        assertEquals("TEMPLATE_TEXT(<p>) {{({{) IDENTIFIER(user) .(.) IDENTIFIER(name) |(|) IDENTIFIER(upper) }}(}}) TEMPLATE_TEXT(</p>)",
                lex("<p>{{ user.name | upper }}</p>"));
    }

    public void testBlockTagAndKeywords() {
        assertEquals("{%({%-) TAG_NAME(if) IDENTIFIER(a) and(and) not(not) IDENTIFIER(b) %}(-%})",
                lex("{%- if a and not b -%}"));
    }

    public void testSoftKeywordsOnlyInsideBlockTags() {
        assertEquals("{%({%) TAG_NAME(import) STRING(\"forms.njk\") as(as) IDENTIFIER(forms) %}(%}) "
                        + "{{({{) IDENTIFIER(as) }}(}})",
                lex("{% import \"forms.njk\" as forms %}{{ as }}"));
    }

    public void testCommentAndRaw() {
        assertEquals("COMMENT({# hi #}) {%({%) TAG_NAME(raw) %}(%}) TEMPLATE_TEXT({{ not code }}) "
                        + "{%({%) TAG_NAME(endraw) %}(%})",
                lex("{# hi #}{% raw %}{{ not code }}{% endraw %}"));
    }

    public void testDictInsideOutput() {
        assertEquals("{{({{) {({) IDENTIFIER(a) :(:) {({) IDENTIFIER(b) :(:) NUMBER(1) }(}) }(}) }}(}})",
                lex("{{ {a: {b: 1}} }}"));
    }

    public void testFrontMatter() {
        assertEquals("FRONT_MATTER(---\ntitle: x\n---) TEMPLATE_TEXT(\n) {{({{) IDENTIFIER(title) }}(}})",
                lex("---\ntitle: x\n---\n{{ title }}"));
    }

    public void testUnterminatedTagRecovers() {
        assertEquals("{%({%) TAG_NAME(if) IDENTIFIER(x) {{({{) IDENTIFIER(y) }}(}})", lex("{% if x {{ y }}"));
    }

    /** The editor re-lexes from the middle of a file using the saved state; every restart must give the same tokens. */
    public void testRestartFromAnyTokenGivesSameTokens() {
        String text = "---\na: 1\n---\n<p>{{ {x: {y: 1}} }}</p>{% raw %}{{ no }}{% endraw %}{# c #}"
                + "{% verbatim %}{% if %}{% endverbatim %}{% if a %}{{ b | f(1, 'x') }}{% endif %}";
        java.util.List<int[]> full = new java.util.ArrayList<>();
        NjkLexer lexer = new NjkLexer();
        lexer.start(text);
        while (lexer.getTokenType() != null) {
            full.add(new int[]{lexer.getTokenStart(), lexer.getState(), lexer.getTokenType().getIndex()});
            lexer.advance();
        }
        for (int i = 1; i < full.size(); i++) {
            NjkLexer restarted = new NjkLexer();
            restarted.start(text, full.get(i)[0], text.length(), full.get(i)[1]);
            for (int j = i; j < full.size(); j++) {
                assertEquals("restart at token " + i + ", token " + j, full.get(j)[2], restarted.getTokenType().getIndex());
                assertEquals(full.get(j)[0], restarted.getTokenStart());
                restarted.advance();
            }
            assertNull(restarted.getTokenType());
        }
    }

    public void testRegexLiteral() {
        assertEquals("{{({{) IDENTIFIER(x) |(|) IDENTIFIER(replace) ((() REGEX(r/[^a-z0-9]+/g) ,(,) STRING(\"-\") )())"
                        + " |(|) IDENTIFIER(replace) ((() REGEX(r/a\\/b/) ,(,) STRING('') )()) /(/) IDENTIFIER(r) }}(}})",
                lex("{{ x | replace(r/[^a-z0-9]+/g, \"-\") | replace(r/a\\/b/, '') / r }}"));
    }

    public void testOperators() {
        assertEquals("{{({{) NUMBER(1) +(+) NUMBER(2.5) **(**) NUMBER(2) //(//) NUMBER(3) ==(===) IDENTIFIER(x) ~(~) STRING('s') }}(}})",
                lex("{{ 1 + 2.5 ** 2 // 3 === x ~ 's' }}"));
    }
}
