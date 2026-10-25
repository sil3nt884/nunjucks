package dev.njk.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.NjkFile;
import dev.njk.psi.*;
import dev.njk.types.NjkMember;
import dev.njk.types.NjkType;
import dev.njk.types.NjkTypeAnnotations;
import dev.njk.types.NjkTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class NjkResolver {
    private NjkResolver() {
    }

    /** Local declaration if there is one, otherwise every place the name may come from. */
    public static @NotNull List<PsiElement> resolveVariable(@NotNull NjkReferenceExpression ref) {
        String name = ref.getName();
        PsiNamedElement local = NjkScopes.findLocal(ref, name);
        if (local != null) return List.of(finalTarget(local));
        if (!(ref.getContainingFile() instanceof NjkFile file)) return List.of();
        List<PsiElement> result = new ArrayList<>();
        for (NjkContext.Source source : NjkContext.external(file).getOrDefault(name, List.of())) {
            result.add(source.target());
        }
        if (result.isEmpty()) {
            NjkTypeAnnotations.Annotation annotation = NjkTypeAnnotations.find(ref, name);
            if (annotation != null) {
                result.add(new NjkSourceTarget(file, annotation.nameOffset(), name, "@type " + annotation.typeText()));
            }
        }
        return result;
    }

    /** {@code {% from "x" import field %}}: usages of {@code field} go straight to the macro. */
    public static @NotNull PsiElement finalTarget(@NotNull PsiNamedElement declaration) {
        if (declaration instanceof NjkImportedName imported) {
            PsiReference ref = imported.getReference();
            PsiElement macro = ref != null ? ref.resolve() : null;
            if (macro != null) return macro;
        }
        return declaration;
    }

    public static @NotNull List<PsiElement> resolveAll(@Nullable PsiElement element) {
        if (element == null) return List.of();
        PsiReference ref = element.getReference();
        if (ref == null) return List.of();
        if (ref instanceof PsiPolyVariantReference poly) {
            List<PsiElement> result = new ArrayList<>();
            for (ResolveResult r : poly.multiResolve(false)) {
                if (r.getElement() != null) result.add(r.getElement());
            }
            return result;
        }
        PsiElement target = ref.resolve();
        return target != null ? List.of(target) : List.of();
    }

    /** Where {@code qualifier.name} is declared: a dict key, a macro of an imported template, a TypeScript property. */
    public static @NotNull List<PsiElement> resolveMember(@NotNull NjkMemberExpression member) {
        String name = member.getMemberName();
        if (name == null) return List.of();
        NjkType qualifier = NjkTypes.typeOf(member.getQualifier());
        NjkMember m = qualifier != null ? qualifier.members().get(name) : null;
        return m != null && m.declaration() != null ? List.of(m.declaration()) : List.of();
    }

    /** Blocks with the same name in the templates this file extends, nearest first. */
    public static @NotNull List<NjkBlockName> overriddenBlocks(@NotNull NjkBlockName block) {
        List<NjkBlockName> result = new ArrayList<>();
        String name = block.getName();
        if (name == null || !(block.getContainingFile() instanceof NjkFile file)) return result;
        for (NjkFile parent : NjkLookup.parentTemplates(file)) {
            NjkBlockName found = findBlock(parent, name);
            if (found != null) result.add(found);
        }
        return result;
    }

    /** Blocks with the same name in templates extending this file. */
    public static @NotNull List<NjkBlockName> overridingBlocks(@NotNull NjkBlockName block) {
        List<NjkBlockName> result = new ArrayList<>();
        String name = block.getName();
        if (name == null || !(block.getContainingFile() instanceof NjkFile file)) return result;
        for (NjkTag tag : NjkLookup.referencingTags(file)) {
            if (!"extends".equals(tag.getTagName())) continue;
            NjkBlockName found = findBlock(tag.getContainingFile(), name);
            if (found != null) result.add(found);
        }
        return result;
    }

    public static @Nullable NjkBlockName findBlock(@NotNull PsiElement file, @NotNull String name) {
        for (NjkBlockName candidate : PsiTreeUtil.findChildrenOfType(file, NjkBlockName.class)) {
            if (candidate.isDefinition() && name.equals(candidate.getName())) return candidate;
        }
        return null;
    }
}
