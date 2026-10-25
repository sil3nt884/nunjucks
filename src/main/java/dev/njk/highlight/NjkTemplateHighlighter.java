package dev.njk.highlight;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import dev.njk.NjkFileType;
import dev.njk.lexer.NjkTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Highlights Nunjucks tokens itself and delegates template text to the template data language highlighter. */
public final class NjkTemplateHighlighter extends LayeredLexerEditorHighlighter {
    public NjkTemplateHighlighter(@Nullable Project project, @Nullable VirtualFile file, @NotNull EditorColorsScheme colors) {
        super(new NjkSyntaxHighlighter(), colors);
        FileType dataType = null;
        if (project != null && file != null) {
            Language language = TemplateDataLanguageMappings.getInstance(project).getMapping(file);
            if (language != null) dataType = language.getAssociatedFileType();
        }
        if (dataType == null) dataType = NjkFileType.defaultTemplateDataFileType();
        SyntaxHighlighter outer = SyntaxHighlighterFactory.getSyntaxHighlighter(dataType, project, file);
        if (outer != null) {
            registerLayer(NjkTokenTypes.TEMPLATE_TEXT, new LayerDescriptor(outer, ""));
        }
    }
}
