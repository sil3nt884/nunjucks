package dev.njk;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class NjkFile extends PsiFileBase {
    public NjkFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, NjkLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return NjkFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Nunjucks file";
    }
}
