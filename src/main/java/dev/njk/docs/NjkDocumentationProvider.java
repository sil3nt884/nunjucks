package dev.njk.docs;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.completion.NjkBuiltins;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.psi.*;
import dev.njk.resolve.NjkSourceTarget;
import dev.njk.types.NjkType;
import dev.njk.types.NjkTypeProvider;
import dev.njk.types.NjkTypes;
import org.jetbrains.annotations.Nullable;

public final class NjkDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(Editor editor, PsiFile file, @Nullable PsiElement contextElement,
                                                              int targetOffset) {
        if (contextElement == null) return null;
        PsiElement parent = contextElement.getParent();
        if (contextElement.getNode().getElementType() == NjkTokenTypes.TAG_NAME
                && NjkBuiltins.TAGS.containsKey(contextElement.getText())) {
            return contextElement;
        }
        if (parent instanceof NjkFilterName filter && builtinFilter(filter.getName()) != null
                && filter.getReference() != null && filter.getReference().resolve() == null) {
            return parent;
        }
        if (parent instanceof NjkTestName) return parent;
        if (parent instanceof NjkReferenceExpression ref && NjkBuiltins.GLOBALS.containsKey(ref.getName())
                && ref.getReference() != null && ref.getReference().resolve() == null) {
            return parent;
        }
        return null;
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element == null) return null;
        if (element.getNode() != null && element.getNode().getElementType() == NjkTokenTypes.TAG_NAME) {
            return builtinDoc(NjkBuiltins.TAGS.get(element.getText()), "tag");
        }
        if (element instanceof NjkFilterName filter) return builtinDoc(builtinFilter(filter.getName()), "filter");
        if (element instanceof NjkTestName test) return builtinDoc(NjkBuiltins.TESTS.get(test.getName()), "test");
        if (element instanceof NjkReferenceExpression ref) return builtinDoc(NjkBuiltins.GLOBALS.get(ref.getName()), "global function");
        if (element instanceof NjkSourceTarget target) {
            NjkTypeProvider.FunctionInfo info = NjkTypes.functionInfo(target);
            String signature = info != null
                    ? info.parameters() + (info.returnTypeText().isEmpty() ? "" : ": " + info.returnTypeText())
                    : typeSuffix(NjkTypes.typeOfDeclaration(target));
            return definition(target.getName(), signature) + content(StringUtil.escapeXmlEntities(target.getLocationString()));
        }
        if (element instanceof NjkNamedElementBase named) {
            String location = named.getKindDescription() + " in " + named.getContainingFile().getName();
            NjkTag tag = PsiTreeUtil.getParentOfType(named, NjkTag.class);
            String code = tag != null ? "<pre>" + StringUtil.escapeXmlEntities(tag.getText()) + "</pre>" : "";
            String signature = named instanceof NjkVariable v && v.getKind() == NjkVariable.Kind.MACRO
                    ? macroSignature(v) : typeSuffix(NjkTypes.typeOfDeclaration(named));
            return definition(named.getName(), signature) + content(StringUtil.escapeXmlEntities(location) + code);
        }
        if (element instanceof NjkDictKey key) {
            return definition(key.getName(), null) + content("key in " + StringUtil.escapeXmlEntities(key.getContainingFile().getName()));
        }
        return null;
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        if (element instanceof NjkSourceTarget target) return target.getName() + " — " + target.getLocationString();
        if (element instanceof NjkNamedElementBase named) {
            return named.getKindDescription() + " " + named.getName() + " — " + named.getContainingFile().getName();
        }
        if (element instanceof PsiNamedElement named && element.getContainingFile() != null) {
            return named.getName() + " — " + element.getContainingFile().getName();
        }
        return null;
    }

    private static @Nullable String typeSuffix(@Nullable NjkType type) {
        return type != null ? ": " + type.presentableText() : null;
    }

    private static @Nullable NjkBuiltins.Item builtinFilter(String name) {
        NjkBuiltins.Item item = NjkBuiltins.FILTERS.get(name);
        return item != null ? item : NjkBuiltins.ELEVENTY_FILTERS.get(name);
    }

    private static String macroSignature(NjkVariable macro) {
        StringBuilder sb = new StringBuilder("(");
        for (NjkVariable param : macro.getDeclaringTag().getParameters()) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(param.getName());
        }
        return sb.append(")").toString();
    }

    private static @Nullable String builtinDoc(@Nullable NjkBuiltins.Item item, String kind) {
        if (item == null) return null;
        return definition(item.name(), item.signature()) + content(StringUtil.escapeXmlEntities(item.description()))
                + DocumentationMarkup.SECTIONS_START + DocumentationMarkup.SECTION_HEADER_START + "Built-in"
                + DocumentationMarkup.SECTION_SEPARATOR + kind + DocumentationMarkup.SECTION_END + DocumentationMarkup.SECTIONS_END;
    }

    private static String definition(String name, @Nullable String signature) {
        String sig = signature == null || signature.isEmpty() ? "" : signature.startsWith("(") || signature.startsWith(":") ? signature : " " + signature;
        return DocumentationMarkup.DEFINITION_START + "<b>" + StringUtil.escapeXmlEntities(name) + "</b>"
                + StringUtil.escapeXmlEntities(sig) + DocumentationMarkup.DEFINITION_END;
    }

    private static String content(String html) {
        return DocumentationMarkup.CONTENT_START + html + DocumentationMarkup.CONTENT_END;
    }
}
