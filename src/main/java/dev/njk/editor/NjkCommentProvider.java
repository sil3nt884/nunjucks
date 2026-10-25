package dev.njk.editor;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.MultipleLangCommentProvider;
import dev.njk.template.NjkFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Uses {@code {# #}} for "Comment with Line/Block Comment" everywhere in a template, including HTML parts. */
public final class NjkCommentProvider implements MultipleLangCommentProvider {
    private static final Commenter COMMENTER = new NjkCommenter();

    @Override
    public @Nullable Commenter getLineCommenter(@NotNull PsiFile file, @NotNull Editor editor,
                                                @NotNull Language lineStartLanguage, @NotNull Language lineEndLanguage) {
        return COMMENTER;
    }

    @Override
    public boolean canProcess(@NotNull PsiFile file, @NotNull FileViewProvider viewProvider) {
        return viewProvider instanceof NjkFileViewProvider;
    }
}
