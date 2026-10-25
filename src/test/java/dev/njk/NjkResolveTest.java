package dev.njk;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.njk.psi.NjkBlockName;
import dev.njk.psi.NjkDictKey;
import dev.njk.psi.NjkVariable;
import dev.njk.resolve.NjkResolver;
import dev.njk.resolve.NjkSourceTarget;

import java.util.Arrays;
import java.util.List;

public class NjkResolveTest extends BasePlatformTestCase {

    private List<PsiElement> resolveAtCaret() {
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at caret", ref);
        if (ref instanceof PsiPolyVariantReference poly) {
            return Arrays.stream(poly.multiResolve(false)).map(ResolveResult::getElement).toList();
        }
        PsiElement target = ref.resolve();
        return target == null ? List.of() : List.of(target);
    }

    private PsiElement single() {
        List<PsiElement> targets = resolveAtCaret();
        assertEquals("targets: " + targets, 1, targets.size());
        return targets.get(0);
    }

    public void testSetVariable() {
        myFixture.configureByText("a.njk", "{% set name = 'x' %}<p>{{ na<caret>me }}</p>");
        PsiElement target = single();
        assertInstanceOf(target, NjkVariable.class);
        assertEquals(NjkVariable.Kind.SET, ((NjkVariable) target).getKind());
    }

    public void testSetInsideIfIsVisibleAfterIt() {
        myFixture.configureByText("a.njk", "{% if a %}{% set name = 1 %}{% endif %}{{ na<caret>me }}");
        assertInstanceOf(single(), NjkVariable.class);
    }

    public void testLoopVariableIsScopedToLoop() {
        myFixture.configureByText("a.njk", "{% for item in items %}{{ item }}{% endfor %}{{ it<caret>em }}");
        assertEmpty(resolveAtCaret());
    }

    public void testLoopVariable() {
        myFixture.configureByText("a.njk", "{% for k, item in items %}{{ it<caret>em }}{% endfor %}");
        assertEquals(NjkVariable.Kind.LOOP, ((NjkVariable) single()).getKind());
    }

    public void testShadowingPicksNearest() {
        myFixture.configureByText("a.njk", "{% set x = 1 %}{% set x = 2 %}{{ <caret>x }}");
        PsiElement target = single();
        assertTrue(target.getParent().getText().contains("2"));
    }

    public void testMacroParameter() {
        myFixture.configureByText("a.njk", "{% macro card(post, size=1) %}{{ si<caret>ze }}{% endmacro %}");
        assertEquals(NjkVariable.Kind.PARAMETER, ((NjkVariable) single()).getKind());
    }

    public void testMacroUsedBeforeDefinition() {
        myFixture.configureByText("a.njk", "{{ ca<caret>rd() }}{% macro card() %}x{% endmacro %}");
        assertEquals(NjkVariable.Kind.MACRO, ((NjkVariable) single()).getKind());
    }

    public void testImportAliasMemberResolvesToMacro() {
        myFixture.addFileToProject("macros/forms.njk", "{% macro field(name) %}<input name=\"{{ name }}\">{% endmacro %}");
        myFixture.configureByText("page.njk", "{% import \"macros/forms.njk\" as forms %}{{ forms.fi<caret>eld('a') }}");
        PsiElement target = single();
        assertEquals("forms.njk", target.getContainingFile().getName());
        assertEquals("field", ((NjkVariable) target).getName());
    }

    public void testFromImportResolvesToMacro() {
        myFixture.addFileToProject("macros/forms.njk", "{% macro field(name) %}x{% endmacro %}");
        myFixture.configureByText("page.njk", "{% from \"macros/forms.njk\" import field %}{{ fie<caret>ld('a') }}");
        PsiElement target = single();
        assertEquals("forms.njk", target.getContainingFile().getName());
    }

    public void testFromImportAliasResolvesToAlias() {
        myFixture.addFileToProject("macros/forms.njk", "{% macro field(name) %}x{% endmacro %}");
        myFixture.configureByText("page.njk", "{% from \"macros/forms.njk\" import field as f %}{{ <caret>f('a') }}");
        assertEquals(NjkVariable.Kind.FROM_ALIAS, ((NjkVariable) single()).getKind());
    }

    public void testTemplatePath() {
        myFixture.addFileToProject("templates/partials/card.njk", "card");
        myFixture.configureByText("page.njk", "{% include \"partials/ca<caret>rd.njk\" %}");
        PsiElement target = single();
        assertInstanceOf(target, PsiFile.class);
        assertEquals("card.njk", ((PsiFile) target).getName());
    }

    public void testVariableFromIncludingTemplate() {
        myFixture.addFileToProject("views/page.njk",
                "{% set user = { name: 'Ann' } %}{% include \"partials/card.njk\" %}");
        PsiFile card = myFixture.addFileToProject("views/partials/card.njk", "<b>{{ user.name }}</b>");
        myFixture.configureFromExistingVirtualFile(card.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(card.getText().indexOf("user") + 1);
        PsiElement target = single();
        assertEquals("page.njk", target.getContainingFile().getName());

        myFixture.getEditor().getCaretModel().moveToOffset(card.getText().indexOf("name") + 1);
        PsiElement key = single();
        assertInstanceOf(key, NjkDictKey.class);
    }

    public void testLayoutSeesVariablesSetInChild() {
        myFixture.addFileToProject("views/home.njk", "{% extends \"base.njk\" %}{% set pageTitle = 'Home' %}");
        PsiFile base = myFixture.addFileToProject("views/base.njk", "<title>{{ pageTitle }}</title>");
        myFixture.configureFromExistingVirtualFile(base.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(base.getText().indexOf("pageTitle") + 2);
        assertEquals("home.njk", single().getContainingFile().getName());
    }

    public void testVariableFromExpressRender() {
        myFixture.addFileToProject("routes/profile.js", """
                router.get('/profile', (req, res) => {
                  const posts = load();
                  res.render('profile', {
                    user: req.user, // the logged in user
                    "title": `Profile`,
                    posts,
                    count: posts.filter(p => ({ x: p })).length,
                  });
                });
                """);
        PsiFile template = myFixture.addFileToProject("views/profile.njk", "{{ title }} {{ posts }} {{ count }} {{ x }}");
        myFixture.configureFromExistingVirtualFile(template.getVirtualFile());
        for (String name : List.of("title", "posts", "count")) {
            myFixture.getEditor().getCaretModel().moveToOffset(template.getText().indexOf(name) + 1);
            PsiElement target = single();
            // a text match without the JavaScript plugin, the JS property itself with it
            assertEquals(name, ((PsiNamedElement) target).getName());
            assertEquals("profile.js", target.getContainingFile().getName());
        }
        myFixture.getEditor().getCaretModel().moveToOffset(template.getText().indexOf("x }}"));
        assertEmpty(resolveAtCaret());
    }

    public void testCustomFilterAndGlobal() {
        myFixture.addFileToProject("app.js", """
                const env = nunjucks.configure('views');
                env.addFilter('money', (v) => '$' + v);
                env.addGlobal('siteName', 'Example');
                app.locals.currentYear = 2026;
                """);
        myFixture.configureByText("a.njk", "{{ price | mon<caret>ey }}");
        assertEquals("app.js", single().getContainingFile().getName());

        myFixture.configureByText("b.njk", "{{ site<caret>Name }}");
        assertEquals("app.js", single().getContainingFile().getName());

        myFixture.configureByText("c.njk", "{{ current<caret>Year }}");
        assertEquals("app.js", single().getContainingFile().getName());
    }

    public void testFrontMatterAndEleventyLayout() {
        myFixture.configureByText("post.njk", "---\ntitle: Hi\n---\n{{ ti<caret>tle }}");
        assertInstanceOf(single(), NjkSourceTarget.class);

        myFixture.addFileToProject("posts/first.md", "---\nlayout: post-layout.njk\nheading: First\n---\n# Body");
        PsiFile layout = myFixture.addFileToProject("_includes/post-layout.njk", "<h1>{{ heading }}</h1>");
        myFixture.configureFromExistingVirtualFile(layout.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(layout.getText().indexOf("heading") + 1);
        assertEquals("first.md", single().getContainingFile().getName());
    }

    public void testBlockOverrides() {
        myFixture.addFileToProject("base.njk", "{% block content %}default{% endblock %}");
        myFixture.configureByText("child.njk", "{% extends \"base.njk\" %}{% block con<caret>tent %}mine{% endblock %}");
        PsiElement target = single();
        assertInstanceOf(target, NjkBlockName.class);
        assertEquals("base.njk", target.getContainingFile().getName());
        assertEquals(1, NjkResolver.overridingBlocks((NjkBlockName) target).size());
    }

    public void testRenameLocalVariable() {
        myFixture.configureByText("a.njk", "{% set us<caret>er = 1 %}{{ user }}{% if user %}{{ user + 1 }}{% endif %}");
        myFixture.renameElementAtCaret("member");
        myFixture.checkResult("{% set member = 1 %}{{ member }}{% if member %}{{ member + 1 }}{% endif %}");
    }
}
