package dev.njk;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.njk.parser.NjkElementTypes;
import dev.njk.psi.NjkStatement;

import java.util.Collection;

public class NjkParsingTest extends BasePlatformTestCase {

    private static final String SAMPLE = """
            ---
            title: Hello
            ---
            {% extends "layouts/base.njk" %}
            {% import "macros/forms.njk" as forms %}
            {% from "macros/forms.njk" import field, label as lbl with context %}
            {% set items = [1, 2, 3] %}
            {% set user = { name: "Ann", "role": 'admin', nested: { a: 1 } } %}
            {% set captured %}<b>{{ user.name }}</b>{% endset %}
            {% macro card(post, compact=false) %}
              <article class="{{ 'small' if compact else 'big' }}">
                {{ post.title | default("Untitled") | truncate(20, end="...") }}
                {{ caller() if caller }}
              </article>
            {% endmacro %}
            {% block content %}
              {{ super() }}
              {% for k, v in user %}
                {{ loop.index }}: {{ k }} = {{ v }}
              {% else %}
                nothing
              {% endfor %}
              {% if items | length > 2 and not (user.role == "guest") %}
                big
              {% elif user.name is defined and user.name is not none %}
                named
              {% else %}
                {{ -1 * 2 ** 3 // 4 % 5 ~ "!" }}
              {% endif %}
              {% if "a" in items or 1 not in items %}{% endif %}
              {% call(x) card(user) %}inner {{ x }}{% endcall %}
              {% filter upper %}shout{% endfilter %}
              {% include "partials/footer.njk" ignore missing %}
              {% raw %}{{ untouched }}{% endraw %}
              {% image "cover.png", alt="Cover" %}
              {% markdown %}# Title{% endmarkdown %}
              {{ forms.field("email", type="email") }}
              {{ items[0] }} {{ range(0, 5) | join(",") }}
            {% endblock content %}
            """;

    public void testSampleParsesWithoutErrors() {
        PsiFile file = myFixture.configureByText("page.njk", SAMPLE);
        Collection<PsiErrorElement> errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
        assertEmpty(errors.stream().map(e -> e.getErrorDescription() + " at " + e.getTextOffset()).toList());
    }

    public void testBlockStructure() {
        PsiFile file = myFixture.configureByText("page.njk", SAMPLE);
        Collection<NjkStatement> statements = PsiTreeUtil.findChildrenOfType(file, NjkStatement.class);
        assertTrue(statements.stream().anyMatch(s -> s.is(NjkElementTypes.SET_STATEMENT)));
        assertTrue(statements.stream().anyMatch(s -> s.is(NjkElementTypes.CUSTOM_STATEMENT) && "markdown".equals(s.getTagName())));
        assertFalse("image has no end tag", statements.stream().anyMatch(s -> "image".equals(s.getTagName())));
        for (NjkStatement s : statements) {
            assertNotNull("statement " + s.getTagName() + " should be closed", s.getEndTag());
        }
    }

    public void testMissingEndTagIsReported() {
        PsiFile file = myFixture.configureByText("broken.njk", "{% for x in xs %}{% if x %}{{ x }}{% endfor %}");
        Collection<PsiErrorElement> errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
        assertEquals(1, errors.size());
        assertEquals("Missing {% endif %}", errors.iterator().next().getErrorDescription());
    }

    public void testHtmlAndNunjucksHighlightingHasNoErrors() {
        myFixture.configureByText("page.njk", """
                <ul class="{{ cls }}" {% if active %}data-active{% endif %}>
                  {% for item in items %}
                    <li><a href="{{ item.url }}">{{ item.title | upper }}</a></li>
                  {% endfor %}
                </ul>
                """);
        myFixture.checkHighlighting(false, false, false);
    }

    public void testSyntaxErrorIsHighlighted() {
        myFixture.configureByText("page.njk", "<p>{{ user. }}</p>");
        assertTrue(myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
                .anyMatch(info -> "Name expected".equals(info.getDescription())));
    }
}
