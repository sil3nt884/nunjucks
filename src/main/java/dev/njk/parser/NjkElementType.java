package dev.njk.parser;

import com.intellij.psi.tree.IElementType;
import dev.njk.NjkLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class NjkElementType extends IElementType {
    public NjkElementType(@NotNull @NonNls String debugName) {
        super(debugName, NjkLanguage.INSTANCE);
    }
}
