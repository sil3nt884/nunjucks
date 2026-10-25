package dev.njk.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.parser.NjkElementTypes;
import dev.njk.parser.NjkTagSpec;
import dev.njk.resolve.NjkCustomTagReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A single {@code {% ... %}} tag. */
public final class NjkTag extends NjkPsiElement {
    private static final Set<String> TEMPLATE_TAGS = Set.of("extends", "include", "import", "from");
    private static final Set<String> LOOP_TAGS = Set.of("for", "asyncEach", "asyncAll");

    public NjkTag(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable PsiElement getTagNameElement() {
        return findChildByType(NjkTokenTypes.TAG_NAME);
    }

    public @Nullable String getTagName() {
        PsiElement name = getTagNameElement();
        return name != null ? name.getText() : null;
    }

    public boolean isLoop() {
        String name = getTagName();
        return name != null && LOOP_TAGS.contains(name);
    }

    /** True for extends, include, import and from, whose first argument names another template. */
    public boolean referencesTemplate() {
        String name = getTagName();
        return name != null && TEMPLATE_TAGS.contains(name);
    }

    /** String literal template paths of extends/include/import/from, including those in an include list. */
    public List<NjkStringLiteral> getTemplatePaths() {
        if (!referencesTemplate()) return List.of();
        List<NjkStringLiteral> result = new ArrayList<>();
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof NjkStringLiteral literal) {
                result.add(literal);
            } else if (child.getNode().getElementType() == NjkElementTypes.LIST_LITERAL) {
                result.addAll(PsiTreeUtil.getChildrenOfTypeAsList(child, NjkStringLiteral.class));
            }
        }
        return result;
    }

    /** True for {@code {% include/import/from ... with context %}}. */
    public boolean hasWithContext() {
        return getNode().findChildByType(NjkTokenTypes.KW_WITH) != null;
    }

    /** The expression after {@code in} in {@code {% for x in items %}}. */
    public @Nullable PsiElement getLoopIterable() {
        ASTNode in = getNode().findChildByType(NjkTokenTypes.KW_IN);
        if (in == null) return null;
        PsiElement next = in.getPsi().getNextSibling();
        while (next != null && !(next instanceof NjkPsiElement)) next = next.getNextSibling();
        return next;
    }

    /** The expression after {@code =} in {@code {% set x = value %}}. */
    public @Nullable PsiElement getAssignedValue() {
        ASTNode assign = getNode().findChildByType(NjkTokenTypes.ASSIGN);
        if (assign == null) return null;
        PsiElement next = assign.getPsi().getNextSibling();
        while (next != null && !(next instanceof NjkPsiElement)) next = next.getNextSibling();
        return next;
    }

    public List<NjkVariable> getDirectVariables() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, NjkVariable.class);
    }

    public List<NjkVariable> getParameters() {
        List<NjkVariable> result = new ArrayList<>();
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() != NjkElementTypes.PARAMETER_LIST) continue;
            for (PsiElement param = child.getFirstChild(); param != null; param = param.getNextSibling()) {
                if (param.getNode().getElementType() == NjkElementTypes.PARAMETER) {
                    NjkVariable v = PsiTreeUtil.getChildOfType(param, NjkVariable.class);
                    if (v != null) result.add(v);
                }
            }
        }
        return result;
    }

    /** The macro name, for {@code {% macro name(...) %}}. */
    public @Nullable NjkVariable getMacroName() {
        return "macro".equals(getTagName()) ? PsiTreeUtil.getChildOfType(this, NjkVariable.class) : null;
    }

    /** Names this tag makes visible to the code that follows it. */
    public List<PsiNamedElement> getExportedDeclarations() {
        String name = getTagName();
        if (name == null) return List.of();
        switch (name) {
            case "set", "import" -> {
                return new ArrayList<>(getDirectVariables());
            }
            case "from" -> {
                List<PsiNamedElement> result = new ArrayList<>();
                for (NjkImportedName imported : PsiTreeUtil.findChildrenOfType(this, NjkImportedName.class)) {
                    NjkVariable alias = imported.getAlias();
                    result.add(alias != null ? alias : imported);
                }
                return result;
            }
            default -> {
                return List.of();
            }
        }
    }

    /** Names visible only inside the body of the block this tag opens (loop variables, macro parameters). */
    public List<NjkVariable> getScopeDeclarations() {
        String name = getTagName();
        if (name == null) return List.of();
        if (isLoop()) return getDirectVariables();
        if (name.equals("macro") || name.equals("call")) return getParameters();
        return List.of();
    }

    @Override
    public PsiReference getReference() {
        PsiElement nameElement = getTagNameElement();
        String name = getTagName();
        if (nameElement == null || name == null || NjkTagSpec.isBuiltin(name)) return null;
        return new NjkCustomTagReference(this, nameElement);
    }
}
