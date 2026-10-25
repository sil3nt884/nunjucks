package dev.njk.template;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import dev.njk.NjkFileType;
import dev.njk.NjkLanguage;
import dev.njk.parser.NjkElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Holds two PSI trees for a template: the Nunjucks tree and the template data (HTML) tree. */
public final class NjkFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider
        implements ConfigurableTemplateLanguageFileViewProvider {

    private final Language templateDataLanguage;

    public NjkFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file, boolean eventSystemEnabled) {
        this(manager, file, eventSystemEnabled, computeTemplateDataLanguage(manager, file));
    }

    private NjkFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file, boolean eventSystemEnabled,
                                @NotNull Language templateDataLanguage) {
        super(manager, file, eventSystemEnabled);
        this.templateDataLanguage = templateDataLanguage;
    }

    private static Language computeTemplateDataLanguage(PsiManager manager, VirtualFile file) {
        Language dataLang = TemplateDataLanguageMappings.getInstance(manager.getProject()).getMapping(file);
        if (dataLang == null) dataLang = NjkFileType.defaultTemplateDataFileType().getLanguage();
        Language substituted = LanguageSubstitutors.getInstance().substituteLanguage(dataLang, file, manager.getProject());
        if (TemplateDataLanguageMappings.getTemplateableLanguages().contains(substituted)) {
            dataLang = substituted;
        }
        return dataLang;
    }

    @Override
    public @NotNull Language getBaseLanguage() {
        return NjkLanguage.INSTANCE;
    }

    @Override
    public @NotNull Language getTemplateDataLanguage() {
        return templateDataLanguage;
    }

    @Override
    public @NotNull Set<Language> getLanguages() {
        return Set.of(NjkLanguage.INSTANCE, templateDataLanguage);
    }

    @Override
    protected @NotNull MultiplePsiFilesPerDocumentFileViewProvider cloneInner(@NotNull VirtualFile copy) {
        return new NjkFileViewProvider(getManager(), copy, false, templateDataLanguage);
    }

    @Override
    protected @Nullable PsiFile createFile(@NotNull Language lang) {
        ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
        if (definition == null) return null;
        if (lang.is(templateDataLanguage)) {
            PsiFile file = definition.createFile(this);
            if (file instanceof PsiFileImpl impl) impl.setContentElementType(NjkElementTypes.TEMPLATE_DATA);
            return file;
        }
        if (lang.isKindOf(getBaseLanguage())) return definition.createFile(this);
        return null;
    }
}
