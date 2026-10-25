package dev.njk.lexer;

import com.intellij.psi.tree.IElementType;
import dev.njk.NjkLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class NjkTokenType extends IElementType {
    public NjkTokenType(@NotNull @NonNls String debugName) {
        super(debugName, NjkLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "NjkTokenType." + super.toString();
    }
}
