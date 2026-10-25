package dev.njk.resolve;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Objects;

/**
 * A navigation target at an offset in any file, used for places found by text scanning, such as a key in
 * a {@code res.render()} context object or an {@code addFilter()} call. Works without a JavaScript plugin.
 */
public final class NjkSourceTarget extends FakePsiElement {
    private final PsiFile file;
    private final int offset;
    private final String name;
    private final String description;

    public NjkSourceTarget(@NotNull PsiFile file, int offset, @NotNull String name, @NotNull String description) {
        this.file = file;
        this.offset = offset;
        this.name = name;
        this.description = description;
    }

    /** What this target is, e.g. "render() context". */
    public @NotNull String getDescription() {
        return description;
    }

    @Override
    public PsiElement getParent() {
        return file;
    }

    @Override
    public PsiFile getContainingFile() {
        return file;
    }

    @Override
    public @NotNull Project getProject() {
        return file.getProject();
    }

    @Override
    public PsiManager getManager() {
        return file.getManager();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @NotNull String getPresentableText() {
        return name;
    }

    @Override
    public @Nullable String getLocationString() {
        int line = lineNumber();
        return description + " · " + file.getName() + (line >= 0 ? ":" + (line + 1) : "");
    }

    private int lineNumber() {
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        return document != null && offset <= document.getTextLength() ? document.getLineNumber(offset) : -1;
    }

    @Override
    public @Nullable Icon getIcon(boolean open) {
        return file.getIcon(0);
    }

    @Override
    public int getTextOffset() {
        return offset;
    }

    @Override
    public TextRange getTextRange() {
        return TextRange.from(offset, name.length());
    }

    @Override
    public String getText() {
        return name;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void navigate(boolean requestFocus) {
        VirtualFile vf = file.getVirtualFile();
        if (vf != null) new OpenFileDescriptor(getProject(), vf, offset).navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return file.getVirtualFile() != null;
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NjkSourceTarget other && other.file.equals(file) && other.offset == offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, offset);
    }
}
