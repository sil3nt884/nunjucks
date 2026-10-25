package dev.njk.editor;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import dev.njk.NjkLanguage;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.template.NjkFileViewProvider;
import org.jetbrains.annotations.NotNull;

/** Typing {@code {{}, {@code {%} or {@code {#} inserts the matching closing delimiter. */
public final class NjkTypedHandler extends TypedHandlerDelegate {
    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (c != '{' && c != '%' && c != '#') return Result.CONTINUE;
        if (!(file.getViewProvider() instanceof NjkFileViewProvider)) return Result.CONTINUE;
        Document document = editor.getDocument();
        CharSequence text = document.getCharsSequence();
        int offset = editor.getCaretModel().getOffset();
        if (offset < 2 || text.charAt(offset - 2) != '{') return Result.CONTINUE;
        if (offset >= 3 && text.charAt(offset - 3) == '{') return Result.CONTINUE;
        char next = offset < text.length() ? text.charAt(offset) : '\0';
        if (offset >= 3 && isInsideNunjucksCode(editor, offset - 3)) return Result.CONTINUE;
        if (next == '}' && (offset + 1 >= text.length() || text.charAt(offset + 1) != '}')) {
            // the first '{' was auto-paired with a single '}': drop it and close the tag properly
            document.deleteString(offset, offset + 1);
        } else if (next == '}' || next == '%' || next == '#') {
            return Result.CONTINUE;
        }

        String close = switch (c) {
            case '{' -> "}}";
            case '%' -> "%}";
            default -> "#}";
        };
        document.insertString(offset, "  " + close);
        editor.getCaretModel().moveToOffset(offset + 1);
        if (c == '%') AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        return Result.STOP;
    }

    private static boolean isInsideNunjucksCode(Editor editor, int offset) {
        if (!(editor instanceof EditorEx ex)) return false;
        HighlighterIterator iterator = ex.getHighlighter().createIterator(offset);
        if (iterator.atEnd()) return false;
        IElementType type = iterator.getTokenType();
        if (type == null || !type.getLanguage().is(NjkLanguage.INSTANCE)) return false;
        return type != NjkTokenTypes.TEMPLATE_TEXT && type != NjkTokenTypes.COMMENT && type != NjkTokenTypes.FRONT_MATTER
                && type != NjkTokenTypes.VAR_END && type != NjkTokenTypes.BLOCK_END;
    }
}
