package dev.njk.types;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A member of an {@link NjkType}. The member type is computed lazily, since member lists of TypeScript types can
 * be long and most members are never looked at.
 *
 * @param declaration where the member is declared (a TS property, a dict key, a macro), if known
 * @param callable    whether the member is a function/method
 */
public record NjkMember(@NotNull String name, @Nullable PsiElement declaration, @NotNull Supplier<@Nullable NjkType> typeSupplier,
                        @NotNull String typeText, boolean callable) {

    public @Nullable NjkType type() {
        return typeSupplier.get();
    }
}
