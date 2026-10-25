package dev.njk.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.NjkFile;
import dev.njk.completion.NjkBuiltins;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.parser.NjkElementTypes;
import dev.njk.psi.NjkDictKey;
import dev.njk.psi.NjkPsiElement;
import dev.njk.psi.NjkTag;
import dev.njk.psi.NjkVariable;
import dev.njk.resolve.NjkLookup;
import dev.njk.resolve.NjkScopes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Types of values built inside templates. */
public final class NjkTemplateTypes {
    private NjkTemplateTypes() {
    }

    /** A type with a fixed name and no members, such as {@code string}. */
    public record Simple(@NotNull String presentableText) implements NjkType {
        @Override
        public @NotNull Map<String, NjkMember> members() {
            return Map.of();
        }
    }

    public static final NjkType STRING = new Simple("string");
    public static final NjkType NUMBER = new Simple("number");
    public static final NjkType BOOLEAN = new Simple("boolean");

    /** {@code {key: value, ...}} */
    public record Dict(@NotNull PsiElement literal) implements NjkType {
        @Override
        public @NotNull Map<String, NjkMember> members() {
            Map<String, NjkMember> result = new LinkedHashMap<>();
            for (PsiElement entry = literal.getFirstChild(); entry != null; entry = entry.getNextSibling()) {
                if (entry.getNode().getElementType() != NjkElementTypes.DICT_ENTRY) continue;
                NjkDictKey key = PsiTreeUtil.getChildOfType(entry, NjkDictKey.class);
                if (key == null) continue;
                PsiElement value = entryValue(key);
                result.putIfAbsent(key.getName(), new NjkMember(key.getName(), key, () -> NjkTypes.typeOf(value),
                        "", false));
            }
            return result;
        }

        @Override
        public @NotNull String presentableText() {
            return "object";
        }
    }

    /** A list of {@code element}, e.g. from a {@code Post[]} type annotation. */
    public record ArrayOf(@NotNull NjkType element) implements NjkType {
        @Override
        public @NotNull Map<String, NjkMember> members() {
            return Map.of("length", new NjkMember("length", null, () -> NUMBER, "number", false));
        }

        @Override
        public @NotNull NjkType elementType() {
            return element;
        }

        @Override
        public @NotNull String presentableText() {
            return element.presentableText() + "[]";
        }
    }

    /** {@code [a, b, c]} */
    public record ListOf(@NotNull PsiElement literal) implements NjkType {
        @Override
        public @NotNull Map<String, NjkMember> members() {
            return Map.of();
        }

        @Override
        public @Nullable NjkType elementType() {
            PsiElement first = PsiTreeUtil.getChildOfType(literal, NjkPsiElement.class);
            return first != null ? NjkTypes.typeOf(first) : null;
        }

        @Override
        public @NotNull String presentableText() {
            NjkType element = elementType();
            return (element != null ? element.presentableText() : "any") + "[]";
        }
    }

    /** {@code {% import "forms.njk" as forms %}}: the macros and top-level variables of the imported template. */
    public record TemplateModule(@NotNull NjkTag importTag) implements NjkType {
        @Override
        public @NotNull Map<String, NjkMember> members() {
            Map<String, NjkMember> result = new LinkedHashMap<>();
            for (NjkFile file : NjkLookup.targetsOf(importTag)) {
                for (NjkVariable macro : NjkScopes.macros(file)) {
                    String name = macro.getName();
                    if (name != null) result.putIfAbsent(name, new NjkMember(name, macro, () -> null, "macro", true));
                }
                NjkScopes.processTopLevel(file, (PsiNamedElement d) -> {
                    String name = d.getName();
                    if (name != null) result.putIfAbsent(name, new NjkMember(name, d, () -> NjkTypes.typeOfDeclaration(d), "", false));
                    return true;
                });
            }
            return result;
        }

        @Override
        public @NotNull String presentableText() {
            return "template";
        }
    }

    /** The {@code loop} variable inside a for loop. */
    public static final NjkType LOOP = new NjkType() {
        @Override
        public @NotNull Map<String, NjkMember> members() {
            Map<String, NjkMember> result = new LinkedHashMap<>();
            for (NjkBuiltins.Item item : NjkBuiltins.LOOP_MEMBERS.values()) {
                NjkType type = item.name().equals("first") || item.name().equals("last") ? BOOLEAN : NUMBER;
                result.put(item.name(), new NjkMember(item.name(), null, () -> type, type.presentableText(), false));
            }
            return result;
        }

        @Override
        public @NotNull String presentableText() {
            return "loop";
        }
    };

    public static @Nullable PsiElement entryValue(@NotNull NjkDictKey key) {
        PsiElement entry = key.getParent();
        if (entry == null) return null;
        boolean afterColon = false;
        for (PsiElement child = entry.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == NjkTokenTypes.COLON) {
                afterColon = true;
            } else if (afterColon && child instanceof NjkPsiElement) {
                return child;
            }
        }
        return null;
    }
}
