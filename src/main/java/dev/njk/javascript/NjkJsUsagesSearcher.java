package dev.njk.javascript;

import com.intellij.lang.javascript.psi.JSElement;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import dev.njk.NjkFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Finds template usages of TypeScript/JavaScript declarations: a property of a render() context object, an
 * interface member used as {@code user.name}, etc. JS use scopes don't include templates, so they are searched
 * here explicitly. This also makes Rename update templates.
 */
public final class NjkJsUsagesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
    public NjkJsUsagesSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters parameters,
                             @NotNull Processor<? super PsiReference> consumer) {
        PsiElement target = parameters.getElementToSearch();
        if (!(target instanceof JSElement) || !(target instanceof PsiNamedElement named)) return;
        String name = named.getName();
        if (name == null || name.isEmpty()) return;
        Project project = target.getProject();
        GlobalSearchScope templates = GlobalSearchScope.getScopeRestrictedByFileTypes(
                GlobalSearchScope.projectScope(project), NjkFileType.INSTANCE);
        PsiSearchHelper.getInstance(project).processElementsWithWord((element, offsetInElement) -> {
            for (PsiReference ref : element.getReferences()) {
                if (ref.getRangeInElement().containsOffset(offsetInElement) && ref.isReferenceTo(target)) {
                    if (!consumer.process(ref)) return false;
                }
            }
            return true;
        }, templates, name, UsageSearchContext.IN_CODE, true);
    }
}
