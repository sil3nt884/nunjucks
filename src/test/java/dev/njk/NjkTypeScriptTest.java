package dev.njk;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.usageView.UsageInfo;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** How templates see TypeScript: context types, filters, globals, annotations, and navigation both ways. */
public class NjkTypeScriptTest extends BasePlatformTestCase {

    private static final String MODELS = """
            export interface Author { name: string; email: string }
            export interface Post { title: string; author: Author; tags: string[] }
            export interface ProfileContext { user: Author; visits: number }
            """;

    private static final String ROUTES = """
            import { Post, ProfileContext } from './models';
            declare const res: { render(view: string, context?: object): void };
            declare const env: { addFilter(name: string, fn: Function): void; addGlobal(name: string, value: any): void };
            declare function load(): Post[];

            const posts: Post[] = load();
            res.render('blog', { posts, featured: posts[0] });

            const profile: ProfileContext = { user: posts[0].author, visits: 3 };
            res.render('profile', profile);

            env.addFilter('initials', (author: Author, upper: boolean = true): string[] => []);
            env.addGlobal('site', { name: 'Example', url: 'https://example.com' });
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("src/models.ts", MODELS);
        myFixture.addFileToProject("src/routes.ts", ROUTES.replace("import { Post, ProfileContext }",
                "import { Author, Post, ProfileContext }"));
    }

    private PsiFile openTemplate(String path, String textWithCaret) {
        int caret = textWithCaret.indexOf("<caret>");
        PsiFile file = myFixture.addFileToProject(path, textWithCaret.replace("<caret>", ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
        return file;
    }

    private List<String> completeAtCaret() {
        LookupElement[] items = myFixture.completeBasic();
        assertNotNull("completion inserted a single item instead of showing a list", items);
        return myFixture.getLookupElementStrings();
    }

    private List<PsiElement> resolveAtCaret() {
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at caret", ref);
        if (ref instanceof PsiPolyVariantReference poly) {
            return Arrays.stream(poly.multiResolve(false)).map(ResolveResult::getElement).toList();
        }
        return ref.resolve() == null ? List.of() : List.of(ref.resolve());
    }

    public void testLoopVariableHasElementTypeOfRenderContextArray() {
        openTemplate("views/blog.njk", "{% for post in posts %}{{ post.author.<caret> }}{% endfor %}");
        assertContainsElements(completeAtCaret(), "name", "email");
    }

    public void testMemberResolvesToInterfaceProperty() {
        openTemplate("views/blog.njk", "{% for post in posts %}{{ post.author.em<caret>ail }}{% endfor %}");
        List<PsiElement> targets = resolveAtCaret();
        assertEquals(1, targets.size());
        assertEquals("models.ts", targets.get(0).getContainingFile().getName());
        assertEquals("email", ((PsiNamedElement) targets.get(0)).getName());
    }

    public void testRenderContextPropertyIsRealTypeScriptElement() {
        openTemplate("views/blog.njk", "{{ feat<caret>ured.title }}");
        List<PsiElement> targets = resolveAtCaret();
        assertEquals(1, targets.size());
        assertInstanceOf(targets.get(0), JSProperty.class);
    }

    public void testContextPassedAsTypedVariable() {
        openTemplate("views/profile.njk", "{{ us<caret>er }}");
        List<PsiElement> targets = resolveAtCaret();
        assertEquals(1, targets.size());
        assertEquals("models.ts", targets.get(0).getContainingFile().getName());

        openTemplate("views/profile2.njk", "");
        myFixture.addFileToProject("src/profile2.ts", """
                import { ProfileContext } from './models';
                declare const res: { render(view: string, context?: object): void };
                declare const ctx: ProfileContext;
                res.render('profile2', ctx);
                """);
        myFixture.type("{{ user.");
        assertContainsElements(completeAtCaret(), "name", "email");
    }

    public void testGlobalValueType() {
        openTemplate("views/layout.njk", "{{ site.<caret> }}");
        assertContainsElements(completeAtCaret(), "name", "url");
    }

    public void testFilterSignatureFromTypeScript() {
        openTemplate("views/blog.njk", "{{ post | <caret> }}");
        LookupElement[] items = myFixture.completeBasic();
        LookupElement initials = Arrays.stream(items).filter(i -> i.getLookupString().equals("initials")).findFirst().orElseThrow();
        LookupElementPresentation presentation = LookupElementPresentation.renderElement(initials);
        assertEquals("(upper?: boolean)", presentation.getTailText());
        assertEquals("string[]", presentation.getTypeText());
    }

    public void testFilterReturnTypeFlowsIntoLoop() {
        openTemplate("views/blog.njk", "{% for i in post.author | initials %}{{ i.<caret> }}{% endfor %}");
        assertContainsElements(completeAtCaret(), "length", "toUpperCase");
    }

    public void testTypeAnnotationWithImport() {
        openTemplate("views/card.njk", "{# @type {import(\"../src/models\").Author} author #}\n{{ author.<caret> }}");
        assertContainsElements(completeAtCaret(), "name", "email");
    }

    public void testTypeAnnotationByNameAndArray() {
        openTemplate("views/list.njk", "{# @type items: Post[] #}\n{% for item in items %}{{ item.<caret> }}{% endfor %}");
        assertContainsElements(completeAtCaret(), "title", "author", "tags");
    }

    public void testMacroParameterAnnotation() {
        openTemplate("views/macros.njk",
                "{# @param {Author} who #}\n{% macro byline(who) %}{{ who.<caret> }}{% endmacro %}");
        assertContainsElements(completeAtCaret(), "name", "email");
    }

    public void testRenderStringNavigatesToTemplateAndFollowsRename() {
        PsiFile template = myFixture.addFileToProject("views/blog.njk", "{{ posts }}");
        PsiFile routes = myFixture.findFileInTempDir("src/routes.ts") != null
                ? getPsiManager().findFile(myFixture.findFileInTempDir("src/routes.ts")) : null;
        assertNotNull(routes);
        myFixture.configureFromExistingVirtualFile(routes.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(routes.getText().indexOf("'blog'") + 2);
        List<PsiElement> targets = resolveAtCaret();
        assertEquals(List.of(template), targets);

        List<GutterMark> gutters = myFixture.findGuttersAtCaret();
        assertTrue(gutters.stream().anyMatch(g -> String.valueOf(g.getTooltipText()).contains("Renders blog.njk")));

        myFixture.renameElement(template, "articles.njk");
        assertTrue(myFixture.getEditor().getDocument().getText().contains("res.render('articles', {"));
    }

    public void testFindUsagesOfInterfacePropertyIncludesTemplates() {
        myFixture.addFileToProject("views/blog.njk", "{% for post in posts %}{{ post.author.email }}{% endfor %}");
        PsiFile models = getPsiManager().findFile(myFixture.findFileInTempDir("src/models.ts"));
        myFixture.configureFromExistingVirtualFile(models.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(models.getText().indexOf("email"));
        Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());
        assertTrue(usages.stream().anyMatch(u -> u.getFile() != null && u.getFile().getName().equals("blog.njk")));
    }

    public void testRenameRenderContextPropertyUpdatesTemplate() {
        PsiFile template = myFixture.addFileToProject("views/blog.njk", "{{ featured.title }}");
        PsiFile routes = getPsiManager().findFile(myFixture.findFileInTempDir("src/routes.ts"));
        myFixture.configureFromExistingVirtualFile(routes.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(routes.getText().indexOf("featured"));
        myFixture.renameElementAtCaret("highlight");
        assertEquals("{{ highlight.title }}", template.getText());
    }

    public void testConfiguredSearchPathDisambiguates() {
        myFixture.addFileToProject("app/server.ts", """
                import * as path from 'path';
                declare const nunjucks: { configure(path: string | string[], options?: object): void };
                nunjucks.configure(path.join(__dirname, 'views'), { autoescape: true });
                """);
        PsiFile wanted = myFixture.addFileToProject("app/views/partials/nav.njk", "nav");
        myFixture.addFileToProject("legacy/partials/nav.njk", "old nav");
        openTemplate("app/views/index.njk", "{% include \"partials/na<caret>v.njk\" %}");
        assertEquals(List.of(wanted), resolveAtCaret());
    }

    public void testViewEngineExtension() {
        myFixture.addFileToProject("site/app.ts", """
                declare const app: { set(key: string, value: string): void };
                app.set('view engine', 'njk');
                app.set('views', __dirname + '/pages');
                """);
        PsiFile njk = myFixture.addFileToProject("site/pages/home.njk", "");
        myFixture.addFileToProject("site/pages/home.html", "");
        PsiFile routes = myFixture.addFileToProject("site/routes.ts", """
                declare const res: { render(view: string): void };
                res.render('home');
                """);
        myFixture.configureFromExistingVirtualFile(routes.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(routes.getText().indexOf("'home'") + 2);
        assertEquals(List.of(njk), resolveAtCaret());
    }
}
