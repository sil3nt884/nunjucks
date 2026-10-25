package dev.njk;

import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import org.jetbrains.annotations.NotNull;

public final class NjkLanguage extends Language implements TemplateLanguage {
    public static final NjkLanguage INSTANCE = new NjkLanguage();

    private NjkLanguage() {
        super("Nunjucks");
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Nunjucks";
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }
}
