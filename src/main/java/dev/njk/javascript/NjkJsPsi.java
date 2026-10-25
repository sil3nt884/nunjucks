package dev.njk.javascript;

import com.intellij.lang.javascript.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Recognizes the Nunjucks-related calls in JavaScript/TypeScript code. */
final class NjkJsPsi {
    /** Calls whose first argument is a template path. */
    static final Set<String> TEMPLATE_METHODS = Set.of("render", "renderFile", "renderAsync", "getTemplate");
    /** Calls whose first argument names something templates can use. */
    static final Set<String> REGISTRATION_METHODS = Set.of(
            "addFilter", "addNunjucksFilter", "addAsyncFilter", "addNunjucksAsyncFilter",
            "addGlobal", "addGlobalData",
            "addShortcode", "addAsyncShortcode", "addPairedShortcode", "addPairedAsyncShortcode",
            "addNunjucksShortcode", "addNunjucksAsyncShortcode", "addPairedNunjucksShortcode",
            "addPairedNunjucksAsyncShortcode", "addNunjucksTag");

    private NjkJsPsi() {
    }

    /** The call that has {@code literal} as its first argument, if any. */
    static @Nullable JSCallExpression callWithFirstArgument(@Nullable PsiElement literal) {
        if (literal == null || !(literal.getParent() instanceof JSArgumentList args)) return null;
        JSExpression[] arguments = args.getArguments();
        if (arguments.length == 0 || arguments[0] != literal) return null;
        return args.getParent() instanceof JSCallExpression call ? call : null;
    }

    static @Nullable String methodName(@Nullable JSCallExpression call) {
        if (call == null) return null;
        return call.getMethodExpression() instanceof JSReferenceExpression ref ? ref.getReferencedName() : null;
    }

    static @Nullable JSExpression argument(JSCallExpression call, int index) {
        JSArgumentList list = call.getArgumentList();
        if (list == null) return null;
        JSExpression[] arguments = list.getArguments();
        return index < arguments.length ? arguments[index] : null;
    }

    /** The string literal containing {@code offset}, e.g. the path of a render call found by the text index. */
    static @Nullable JSLiteralExpression literalAt(PsiElement file, int offset) {
        PsiElement leaf = file.findElementAt(offset);
        return PsiTreeUtil.getParentOfType(leaf, JSLiteralExpression.class, false);
    }
}
