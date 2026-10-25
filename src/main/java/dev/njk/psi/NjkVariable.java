package dev.njk.psi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import dev.njk.parser.NjkElementTypes;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** A name declared by set, for, macro, a macro/call parameter or an import alias. */
public final class NjkVariable extends NjkNamedElementBase {
    public enum Kind {
        SET("variable"), LOOP("loop variable"), MACRO("macro"), PARAMETER("parameter"),
        CALL_PARAMETER("caller parameter"), IMPORT_ALIAS("imported template"), FROM_ALIAS("imported macro"),
        UNKNOWN("variable");

        public final String description;

        Kind(String description) {
            this.description = description;
        }
    }

    public NjkVariable(@NotNull ASTNode node) {
        super(node);
    }

    public @NotNull Kind getKind() {
        PsiElement parent = getParent();
        if (parent == null) return Kind.UNKNOWN;
        if (parent.getNode().getElementType() == NjkElementTypes.PARAMETER) {
            PsiElement tag = parent.getParent() != null ? parent.getParent().getParent() : null;
            if (tag instanceof NjkTag t && "call".equals(t.getTagName())) return Kind.CALL_PARAMETER;
            return Kind.PARAMETER;
        }
        if (parent.getNode().getElementType() == NjkElementTypes.IMPORT_SPECIFIER) return Kind.FROM_ALIAS;
        if (parent instanceof NjkTag tag) {
            if (tag.isLoop()) return Kind.LOOP;
            String name = tag.getTagName();
            if ("set".equals(name)) return Kind.SET;
            if ("macro".equals(name)) return Kind.MACRO;
            if ("import".equals(name)) return Kind.IMPORT_ALIAS;
        }
        return Kind.UNKNOWN;
    }

    public @NotNull NjkTag getDeclaringTag() {
        PsiElement e = getParent();
        while (e != null && !(e instanceof NjkTag)) e = e.getParent();
        return (NjkTag) e;
    }

    @Override
    public @NotNull String getKindDescription() {
        return getKind().description;
    }

    @Override
    public @NotNull SearchScope getUseScope() {
        return switch (getKind()) {
            case LOOP, PARAMETER, CALL_PARAMETER -> new LocalSearchScope(getContainingFile());
            default -> super.getUseScope();
        };
    }

    @Override
    public Icon getIcon(int flags) {
        return switch (getKind()) {
            case MACRO -> AllIcons.Nodes.Function;
            case PARAMETER, CALL_PARAMETER -> AllIcons.Nodes.Parameter;
            case IMPORT_ALIAS -> AllIcons.Nodes.Package;
            case FROM_ALIAS -> AllIcons.Nodes.Function;
            default -> AllIcons.Nodes.Variable;
        };
    }
}
