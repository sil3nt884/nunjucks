package dev.njk.editor;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import dev.njk.lexer.NjkLexer;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.psi.NjkNamedElementBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NjkFindUsagesProvider implements FindUsagesProvider {
    @Override
    public @Nullable WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(new NjkLexer(),
                TokenSet.create(NjkTokenTypes.IDENTIFIER, NjkTokenTypes.TAG_NAME),
                NjkTokenTypes.COMMENTS,
                NjkTokenTypes.STRINGS);
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement element) {
        return element instanceof NjkNamedElementBase;
    }

    @Override
    public @Nullable String getHelpId(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public @NotNull String getType(@NotNull PsiElement element) {
        return element instanceof NjkNamedElementBase named ? named.getKindDescription() : "";
    }

    @Override
    public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        String name = element instanceof PsiNamedElement named ? named.getName() : null;
        return name != null ? name : "";
    }

    @Override
    public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}
