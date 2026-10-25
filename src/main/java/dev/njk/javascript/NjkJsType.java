package dev.njk.javascript;

import com.intellij.lang.javascript.psi.JSFunctionItem;
import com.intellij.lang.javascript.psi.JSFunctionType;
import com.intellij.lang.javascript.psi.JSRecordType;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.JSTypeUtils;
import com.intellij.psi.PsiElement;
import dev.njk.types.NjkMember;
import dev.njk.types.NjkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** A TypeScript/JavaScript type seen from a template. */
final class NjkJsType implements NjkType {
    private final JSType type;
    private final PsiElement context;

    private NjkJsType(JSType type, PsiElement context) {
        this.type = type;
        this.context = context;
    }

    static @Nullable NjkType wrap(@Nullable JSType type, @NotNull PsiElement context) {
        if (type == null || JSTypeUtils.isAnyType(type)) return null;
        return new NjkJsType(type, context);
    }

    private JSType nonNull() {
        JSType stripped = JSTypeUtils.removeNullableComponents(type);
        return stripped != null ? stripped : type;
    }

    @Override
    public @NotNull Map<String, NjkMember> members() {
        Map<String, NjkMember> result = new LinkedHashMap<>();
        JSRecordType record = nonNull().asRecordType(context);
        for (JSRecordType.PropertySignature property : record.getProperties()) {
            if (property.isPrivateName() || !property.hasValidName()) continue;
            String name = property.getMemberName();
            JSType memberType = property.getJSType();
            PsiElement declaration = property.getMemberSource().getSingleElement();
            boolean callable = memberType instanceof JSFunctionType || declaration instanceof JSFunctionItem;
            String typeText = memberType != null ? text(memberType) : "";
            result.putIfAbsent(name, new NjkMember(name, declaration, () -> wrap(memberType, context), typeText, callable));
        }
        return result;
    }

    @Override
    public @Nullable NjkType elementType() {
        JSType base = nonNull();
        JSType element = JSTypeUtils.getIterableComponentType(base);
        if (element == null) element = JSTypeUtils.getIndexableComponentType(base);
        return wrap(element, context);
    }

    @Override
    public @Nullable NjkType returnType() {
        return JSTypeUtils.getFunctionType(nonNull(), false, context)
                .filter(JSFunctionType.class::isInstance)
                .map(f -> wrap(((JSFunctionType) f).getReturnType(), context))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public @NotNull String presentableText() {
        return text(type);
    }

    static String text(JSType type) {
        return type.getTypeText(JSType.TypeTextFormat.PRESENTABLE);
    }
}
