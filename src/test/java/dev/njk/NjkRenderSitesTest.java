package dev.njk;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.njk.actions.NjkGoToRenderSitesAction;
import dev.njk.resolve.NjkRenderSites;

import java.util.List;

public class NjkRenderSitesTest extends BasePlatformTestCase {

    public void testFindsRenderCallsCodeReferencesAndTemplates() {
        myFixture.addFileToProject("apps/manage/src/landing-page/controller.ts", """
                declare const res: { render(view: string, context?: object): void };
                export const getLandingPage = () => res.render('landing-page/landing-page', { title: 'x' });
                """);
        myFixture.addFileToProject("apps/manage/src/landing-page/views.ts",
                "export const LANDING_VIEW = 'landing-page/landing-page.njk';\nexport const OTHER = 'something-else';\n");
        myFixture.addFileToProject("apps/manage/src/forms.njk", "{% include \"landing-page/landing-page.njk\" %}");
        PsiFile template = myFixture.addFileToProject("apps/manage/src/landing-page/landing-page.njk", "<h1>{{ title }}</h1>");

        List<NjkRenderSites.Site> sites = NjkRenderSites.find((NjkFile) template);
        assertEquals(List.of(NjkRenderSites.Kind.RENDER, NjkRenderSites.Kind.CODE, NjkRenderSites.Kind.INCLUDE),
                sites.stream().map(NjkRenderSites.Site::kind).toList());
        assertEquals("controller.ts", sites.get(0).file().getName());
        assertEquals(2, sites.get(0).line());
        assertTrue(sites.get(0).preview().contains("res.render('landing-page/landing-page'"));
        assertEquals("views.ts", sites.get(1).file().getName());
        assertEquals("forms.njk", sites.get(2).file().getName());
    }

    public void testActionJumpsToTheOnlyRenderSite() {
        PsiFile controller = myFixture.addFileToProject("src/controller.ts", """
                declare const res: { render(view: string): void };
                res.render('check-your-answers');
                """);
        PsiFile template = myFixture.addFileToProject("views/check-your-answers.njk", "<h1>Check your answers</h1>");
        myFixture.configureFromExistingVirtualFile(template.getVirtualFile());

        Presentation presentation = myFixture.testAction(new NjkGoToRenderSitesAction());
        assertTrue(presentation.isEnabledAndVisible());
        var selected = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        assertNotNull(selected);
        assertEquals(controller.getVirtualFile(), selected.getVirtualFile());
        assertEquals(controller.getText().indexOf("check-your-answers"), selected.getCaretModel().getOffset());
    }

    public void testActionHiddenOutsideTemplates() {
        PsiFile script = myFixture.addFileToProject("src/app.ts", "export {};");
        myFixture.configureFromExistingVirtualFile(script.getVirtualFile());
        assertFalse(myFixture.testAction(new NjkGoToRenderSitesAction()).isEnabledAndVisible());
    }
}
