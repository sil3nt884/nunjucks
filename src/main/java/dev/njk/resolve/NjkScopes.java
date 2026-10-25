package dev.njk.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import dev.njk.parser.NjkElementTypes;
import dev.njk.psi.NjkStatement;
import dev.njk.psi.NjkTag;
import dev.njk.psi.NjkVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Walks the declarations visible at a point in a template, nearest first.
 * <p>
 * Follows Nunjucks frames: names declared inside for/macro/call/block bodies stay inside; names set inside
 * if/filter/set bodies leak out. Macros are visible anywhere in their file.
 */
public final class NjkScopes {
    private NjkScopes() {
    }

    /** Visits the declarations visible at {@code place}. Stops when the processor returns false. */
    public static boolean processLocal(@NotNull PsiElement place, @NotNull Predicate<PsiNamedElement> processor) {
        PsiElement current = place;
        while (current != null && !(current instanceof PsiFile)) {
            PsiElement parent = current.getParent();
            if (parent instanceof NjkStatement statement) {
                NjkTag open = statement.getOpenTag();
                if (open != null && open != current) {
                    for (NjkVariable v : open.getScopeDeclarations()) {
                        if (!processor.test(v)) return false;
                    }
                }
            }
            for (PsiElement sibling = current.getPrevSibling(); sibling != null; sibling = sibling.getPrevSibling()) {
                if (!processExported(sibling, processor)) return false;
            }
            current = parent;
        }
        PsiFile file = place.getContainingFile();
        if (file != null) {
            for (NjkVariable macro : macros(file)) {
                if (!processor.test(macro)) return false;
            }
        }
        return true;
    }

    /** Visits the declarations visible at the end of the file, i.e. what it exposes to the templates around it. */
    public static boolean processTopLevel(@NotNull PsiFile file, @NotNull Predicate<PsiNamedElement> processor) {
        for (PsiElement child = file.getLastChild(); child != null; child = child.getPrevSibling()) {
            if (!processExported(child, processor)) return false;
        }
        return true;
    }

    /** Declarations that an element exposes to the code following it. */
    private static boolean processExported(PsiElement element, Predicate<PsiNamedElement> processor) {
        if (element instanceof NjkTag tag) {
            for (PsiNamedElement declaration : tag.getExportedDeclarations()) {
                if (!processor.test(declaration)) return false;
            }
        } else if (element instanceof NjkStatement statement) {
            NjkTag open = statement.getOpenTag();
            if (statement.is(NjkElementTypes.SET_STATEMENT) && open != null) {
                for (NjkVariable v : open.getDirectVariables()) {
                    if (!processor.test(v)) return false;
                }
            } else if (statement.is(NjkElementTypes.MACRO_STATEMENT) && open != null) {
                NjkVariable name = open.getMacroName();
                if (name != null && !processor.test(name)) return false;
            } else if (!statement.isScoping()) {
                for (PsiElement child = statement.getLastChild(); child != null; child = child.getPrevSibling()) {
                    if (!processExported(child, processor)) return false;
                }
            }
        }
        return true;
    }

    public static @Nullable PsiNamedElement findLocal(@NotNull PsiElement place, @NotNull String name) {
        PsiNamedElement[] found = new PsiNamedElement[1];
        processLocal(place, declaration -> {
            if (name.equals(declaration.getName())) {
                found[0] = declaration;
                return false;
            }
            return true;
        });
        return found[0];
    }

    /** Macros defined at the top level of a file (or inside non-scoping statements there). */
    public static @NotNull List<NjkVariable> macros(@NotNull PsiFile file) {
        List<NjkVariable> result = new ArrayList<>();
        collectMacros(file, result);
        return result;
    }

    private static void collectMacros(PsiElement container, List<NjkVariable> result) {
        for (PsiElement child = container.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof NjkStatement statement)) continue;
            if (statement.is(NjkElementTypes.MACRO_STATEMENT)) {
                NjkTag open = statement.getOpenTag();
                NjkVariable name = open != null ? open.getMacroName() : null;
                if (name != null) result.add(name);
            } else if (!statement.isScoping()) {
                collectMacros(statement, result);
            }
        }
    }

    public static @Nullable NjkVariable findMacro(@NotNull PsiFile file, @NotNull String name) {
        for (NjkVariable macro : macros(file)) {
            if (name.equals(macro.getName())) return macro;
        }
        return null;
    }

    /** The innermost statement of the given kind containing {@code element} in its body. */
    public static @Nullable NjkStatement enclosingStatement(@NotNull PsiElement element,
                                                            @NotNull com.intellij.psi.tree.IElementType type) {
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile)) {
            PsiElement parent = current.getParent();
            if (parent instanceof NjkStatement statement && statement.is(type) && statement.getOpenTag() != current) {
                return statement;
            }
            current = parent;
        }
        return null;
    }
}
