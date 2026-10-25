package dev.njk;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class NjkCompletionTest extends BasePlatformTestCase {

    private List<String> complete(String text) {
        myFixture.configureByText("test.njk", text);
        myFixture.completeBasic();
        List<String> strings = myFixture.getLookupElementStrings();
        assertNotNull("completion inserted a single item instead of showing a list", strings);
        return strings;
    }

    public void testFilters() {
        myFixture.addFileToProject("app.js", "env.addFilter('money', fn);");
        List<String> items = complete("{{ price | <caret> }}");
        assertContainsElements(items, "upper", "default", "join", "money");
    }

    public void testTags() {
        assertContainsElements(complete("{% <caret> %}"), "if", "for", "block", "macro", "include");
    }

    public void testClosingTagComesFirst() {
        List<String> items = complete("{% for x in xs %}{% if x %}\n{% <caret> %}");
        assertEquals("endif", items.get(0));
        assertContainsElements(items, "elif", "else");
    }

    public void testLoopMembers() {
        assertContainsElements(complete("{% for x in xs %}{{ loop.<caret> }}{% endfor %}"), "index", "first", "last");
    }

    public void testVariablesAndMacros() {
        List<String> items = complete("""
                {% set user = 1 %}{% macro card(post) %}{% endmacro %}
                {% for item in items %}{{ <caret> }}{% endfor %}
                """);
        assertContainsElements(items, "user", "card", "item", "loop", "range");
    }

    public void testExternalVariablesFromRender() {
        myFixture.addFileToProject("server.js", "app.get('/', (q, res) => res.render('home.njk', { headline: 'x', items }));");
        var home = myFixture.addFileToProject("views/home.njk", "");
        myFixture.configureFromExistingVirtualFile(home.getVirtualFile());
        myFixture.type("{{ ");
        myFixture.completeBasic();
        assertContainsElements(myFixture.getLookupElementStrings(), "headline", "items");
    }

    public void testTests() {
        assertContainsElements(complete("{% if x is <caret> %}{% endif %}"), "defined", "odd", "divisibleby");
    }

    public void testImportedMacroNames() {
        myFixture.addFileToProject("forms.njk", "{% macro field() %}{% endmacro %}{% macro label() %}{% endmacro %}");
        assertContainsElements(complete("{% from \"forms.njk\" import <caret> %}"), "field", "label");
    }

    public void testImportAliasMembers() {
        myFixture.addFileToProject("forms.njk", "{% macro field() %}{% endmacro %}{% macro label() %}{% endmacro %}");
        assertContainsElements(complete("{% import \"forms.njk\" as forms %}{{ forms.<caret> }}"), "field", "label");
    }

    public void testDictKeys() {
        assertContainsElements(complete("{% set user = { name: 1, email: 2 } %}{{ user.<caret> }}"), "name", "email");
    }

    public void testTemplatePaths() {
        myFixture.addFileToProject("templates/partials/header.njk", "");
        myFixture.addFileToProject("templates/partials/footer.njk", "");
        myFixture.addFileToProject("templates/layout.njk", "{% include \"partials/header.njk\" %}");
        assertContainsElements(complete("{% include \"partials/<caret>\" %}"), "partials/header.njk", "partials/footer.njk");
    }

    public void testHtmlCompletionBetweenTags() {
        assertContainsElements(complete("{% if x %}<s<caret>{% endif %}"), "section", "span", "strong");
        assertContainsElements(complete("<input ty<caret> value=\"{{ v }}\">"), "type");
    }

    public void testTypingDelimiterInsertsClosing() {
        myFixture.configureByText("t.njk", "<p><caret></p>");
        myFixture.type("{{");
        myFixture.checkResult("<p>{{ <caret> }}</p>");
    }
}
