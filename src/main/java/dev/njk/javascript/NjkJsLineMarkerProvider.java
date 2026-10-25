package dev.njk.javascript;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import dev.njk.NjkFileType;
import dev.njk.NjkIcons;
import dev.njk.psi.NjkPsiElement;
import dev.njk.resolve.NjkResolver;
import dev.njk.resolve.NjkSourceTarget;
import dev.njk.resolve.NjkTemplatePaths;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gutter icons in TypeScript/JavaScript: from {@code render("x")} to the template, and from
 * {@code addFilter("x")}/{@code addGlobal("x")}/shortcodes to the templates using them.
 */
public final class NjkJsLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (element.getFirstChild() != null || !(element.getParent() instanceof JSLiteralExpression literal)) return;
        if (!literal.isQuotedLiteral() || literal.getStringValue() == null) return;
        JSCallExpression call = NjkJsPsi.callWithFirstArgument(literal);
        String method = NjkJsPsi.methodName(call);
        if (method == null) return;
        String value = literal.getStringValue();

        if (NjkJsPsi.TEMPLATE_METHODS.contains(method)) {
            List<PsiFile> templates = new ArrayList<>();
            for (VirtualFile vf : NjkTemplatePaths.resolve(value, literal.getContainingFile())) {
                PsiFile template = literal.getManager().findFile(vf);
                if (template != null) templates.add(template);
            }
            if (!templates.isEmpty()) {
                result.add(NavigationGutterIconBuilder.create(NjkIcons.FILE)
                        .setTargets(templates)
                        .setTooltipText("Renders " + templates.get(0).getName())
                        .createLineMarkerInfo(element));
            }
        } else if (NjkJsPsi.REGISTRATION_METHODS.contains(method)) {
            List<PsiElement> usages = templateUsages(literal, value);
            if (!usages.isEmpty()) {
                result.add(NavigationGutterIconBuilder.create(NjkIcons.FILE)
                        .setTargets(usages)
                        .setTooltipText("'" + value + "' is used in " + usages.size() + " place(s) in templates")
                        .createLineMarkerInfo(element));
            }
        }
    }

    private static List<PsiElement> templateUsages(JSLiteralExpression literal, String name) {
        Project project = literal.getProject();
        PsiFile file = literal.getContainingFile();
        int nameOffset = literal.getTextRange().getStartOffset() + 1;
        Set<PsiElement> usages = new LinkedHashSet<>();
        GlobalSearchScope templates = GlobalSearchScope.getScopeRestrictedByFileTypes(
                GlobalSearchScope.projectScope(project), NjkFileType.INSTANCE);
        PsiSearchHelper.getInstance(project).processElementsWithWord((element, offsetInElement) -> {
            if (!(element instanceof NjkPsiElement) || element.getReference() == null) return true;
            for (PsiElement target : NjkResolver.resolveAll(element)) {
                if (target instanceof NjkSourceTarget source && source.getContainingFile() == file
                        && source.getTextOffset() == nameOffset) {
                    usages.add(element);
                }
            }
            return usages.size() < 100;
        }, templates, name, UsageSearchContext.IN_CODE, true);
        return new ArrayList<>(usages);
    }
}
