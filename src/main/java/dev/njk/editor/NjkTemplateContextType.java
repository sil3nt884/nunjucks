package dev.njk.editor;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import dev.njk.template.NjkFileViewProvider;
import org.jetbrains.annotations.NotNull;

/** Live template context: anywhere in a Nunjucks file. */
public final class NjkTemplateContextType extends TemplateContextType {
    public NjkTemplateContextType() {
        super("Nunjucks");
    }

    @Override
    public boolean isInContext(@NotNull TemplateActionContext context) {
        return context.getFile().getViewProvider() instanceof NjkFileViewProvider;
    }
}
