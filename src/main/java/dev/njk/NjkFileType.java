package dev.njk;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.TemplateLanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class NjkFileType extends LanguageFileType implements TemplateLanguageFileType {
    public static final NjkFileType INSTANCE = new NjkFileType();

    private NjkFileType() {
        super(NjkLanguage.INSTANCE);
    }

    /** The language used for the text between Nunjucks tags unless the user maps it to something else. */
    public static LanguageFileType defaultTemplateDataFileType() {
        return HtmlFileType.INSTANCE;
    }

    @Override
    public @NotNull String getName() {
        return "Nunjucks";
    }

    @Override
    public @NotNull String getDescription() {
        return "Nunjucks template";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "njk";
    }

    @Override
    public Icon getIcon() {
        return NjkIcons.FILE;
    }
}
