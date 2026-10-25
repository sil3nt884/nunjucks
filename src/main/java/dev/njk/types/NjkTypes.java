package dev.njk.types;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.parser.NjkElementTypes;
import dev.njk.psi.*;
import dev.njk.resolve.NjkResolver;
import dev.njk.resolve.NjkScopes;
import dev.njk.resolve.NjkSourceTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Infers the types of template expressions, following values back to the code that provides them. */
public final class NjkTypes {
    private static final Set<String> ELEMENT_FILTERS = Set.of("first", "last", "random");
    private static final Set<String> SAME_TYPE_FILTERS = Set.of("sort", "reverse", "list", "select", "reject",
            "selectattr", "rejectattr", "default", "d", "safe");
    private static final Set<String> STRING_FILTERS = Set.of("join", "upper", "lower", "title", "capitalize", "trim",
            "striptags", "escape", "e", "forceescape", "replace", "truncate", "urlencode", "nl2br", "center", "indent",
            "string", "dump", "urlize");
    private static final Set<String> NUMBER_FILTERS = Set.of("length", "wordcount", "int", "float", "abs", "round", "sum");

    private NjkTypes() {
    }

    public static @Nullable NjkType typeOf(@Nullable PsiElement expression) {
        if (expression == null) return null;
        return RecursionManager.doPreventingRecursion(expression, true, () -> compute(expression));
    }

    private static @Nullable NjkType compute(PsiElement e) {
        IElementType type = e.getNode().getElementType();
        if (e instanceof NjkReferenceExpression ref) return typeOfReference(ref);
        if (e instanceof NjkMemberExpression member) {
            NjkType qualifier = typeOf(member.getQualifier());
            String name = member.getMemberName();
            NjkMember m = qualifier != null && name != null ? qualifier.members().get(name) : null;
            return m != null ? m.type() : null;
        }
        if (type == NjkElementTypes.INDEX_EXPRESSION) {
            List<NjkPsiElement> parts = PsiTreeUtil.getChildrenOfTypeAsList(e, NjkPsiElement.class);
            if (parts.isEmpty()) return null;
            NjkType qualifier = typeOf(parts.get(0));
            if (qualifier == null) return null;
            if (parts.size() > 1 && parts.get(1) instanceof NjkStringLiteral key) {
                NjkMember m = qualifier.members().get(key.getValue());
                return m != null ? m.type() : null;
            }
            return qualifier.elementType();
        }
        if (type == NjkElementTypes.CALL_EXPRESSION) {
            NjkType callee = typeOf(e.getFirstChild());
            return callee != null ? callee.returnType() : null;
        }
        if (type == NjkElementTypes.FILTER_EXPRESSION) return filterResult(e);
        if (type == NjkElementTypes.PARENTHESIZED_EXPRESSION || type == NjkElementTypes.CONDITIONAL_EXPRESSION) {
            return typeOf(PsiTreeUtil.getChildOfType(e, NjkPsiElement.class));
        }
        if (type == NjkElementTypes.DICT_LITERAL) return new NjkTemplateTypes.Dict(e);
        if (type == NjkElementTypes.LIST_LITERAL) return new NjkTemplateTypes.ListOf(e);
        if (type == NjkElementTypes.STRING_LITERAL) return NjkTemplateTypes.STRING;
        if (type == NjkElementTypes.LITERAL) {
            IElementType token = e.getFirstChild().getNode().getElementType();
            if (token == NjkTokenTypes.NUMBER) return NjkTemplateTypes.NUMBER;
            if (token == NjkTokenTypes.KW_TRUE || token == NjkTokenTypes.KW_FALSE) return NjkTemplateTypes.BOOLEAN;
            return null;
        }
        if (type == NjkElementTypes.BINARY_EXPRESSION && e.getNode().findChildByType(NjkTokenTypes.TILDE) != null) {
            return NjkTemplateTypes.STRING;
        }
        return null;
    }

    private static @Nullable NjkType typeOfReference(NjkReferenceExpression ref) {
        String name = ref.getName();
        List<PsiElement> targets = NjkResolver.resolveAll(ref);
        boolean declaredLocally = !targets.isEmpty() && targets.get(0) instanceof NjkPsiElement
                && targets.get(0).getContainingFile() == ref.getContainingFile();
        if (!declaredLocally) {
            NjkType annotated = NjkTypeAnnotations.typeFor(ref, name);
            if (annotated != null) return annotated;
        }
        for (PsiElement target : targets) {
            NjkType type = typeOfDeclaration(target);
            if (type != null) return type;
        }
        if (targets.isEmpty() && name.equals("loop")
                && NjkScopes.enclosingStatement(ref, NjkElementTypes.FOR_STATEMENT) != null) {
            return NjkTemplateTypes.LOOP;
        }
        return null;
    }

    /** Type of a declaration a name resolved to, in a template or in code. */
    public static @Nullable NjkType typeOfDeclaration(@Nullable PsiElement target) {
        if (target == null) return null;
        return RecursionManager.doPreventingRecursion(target, true, () -> computeDeclaration(target));
    }

    private static @Nullable NjkType computeDeclaration(PsiElement target) {
        if (target instanceof NjkVariable variable) {
            String name = variable.getName();
            NjkType annotated = name != null ? NjkTypeAnnotations.typeFor(variable, name) : null;
            if (annotated != null) return annotated;
            NjkTag tag = variable.getDeclaringTag();
            return switch (variable.getKind()) {
                case SET -> tag.getDirectVariables().size() == 1 ? typeOf(tag.getAssignedValue()) : null;
                case LOOP -> {
                    NjkType iterable = typeOf(tag.getLoopIterable());
                    List<NjkVariable> vars = tag.getDirectVariables();
                    if (iterable == null) yield null;
                    if (vars.size() == 1) yield iterable.elementType();
                    // {% for key, value in object %}
                    yield vars.get(0) == variable ? NjkTemplateTypes.STRING : null;
                }
                case IMPORT_ALIAS -> new NjkTemplateTypes.TemplateModule(tag);
                default -> null;
            };
        }
        if (target instanceof NjkDictKey key) return typeOf(NjkTemplateTypes.entryValue(key));
        if (target instanceof NjkPsiElement) return null;
        for (NjkTypeProvider provider : NjkTypeProvider.EP_NAME.getExtensionList()) {
            NjkType type = provider.typeOf(target);
            if (type != null) return type;
        }
        return null;
    }

    private static @Nullable NjkType filterResult(PsiElement filterExpression) {
        NjkFilterName filter = PsiTreeUtil.getChildOfType(filterExpression, NjkFilterName.class);
        if (filter == null) return null;
        String name = filter.getName();
        PsiElement input = filterExpression.getFirstChild();
        if (ELEMENT_FILTERS.contains(name)) {
            NjkType inputType = typeOf(input);
            return inputType != null ? inputType.elementType() : null;
        }
        if (SAME_TYPE_FILTERS.contains(name)) return typeOf(input);
        if (STRING_FILTERS.contains(name)) return NjkTemplateTypes.STRING;
        if (NUMBER_FILTERS.contains(name)) return NjkTemplateTypes.NUMBER;
        for (PsiElement target : NjkResolver.resolveAll(filter)) {
            NjkTypeProvider.FunctionInfo info = functionInfo(target);
            if (info != null && info.returnType() != null) return info.returnType();
        }
        return null;
    }

    public static @Nullable NjkTypeProvider.FunctionInfo functionInfo(@NotNull PsiElement registration) {
        if (!(registration instanceof NjkSourceTarget target)) return null;
        for (NjkTypeProvider provider : NjkTypeProvider.EP_NAME.getExtensionList()) {
            NjkTypeProvider.FunctionInfo info = provider.functionInfo(target);
            if (info != null) return info;
        }
        return null;
    }
}
