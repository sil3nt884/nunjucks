package dev.njk.editor;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.psi.NjkBlockName;
import dev.njk.resolve.NjkResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/** Gutter icons linking a {@code {% block %}} to the blocks it overrides and the blocks overriding it. */
public final class NjkBlockLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (element.getNode().getElementType() != NjkTokenTypes.IDENTIFIER) return;
        if (!(element.getParent() instanceof NjkBlockName block) || !block.isDefinition()) return;

        List<NjkBlockName> overridden = NjkResolver.overriddenBlocks(block);
        if (!overridden.isEmpty()) {
            result.add(NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod)
                    .setTargets(overridden)
                    .setTooltipText("Overrides block '" + block.getName() + "' in " + overridden.get(0).getContainingFile().getName())
                    .createLineMarkerInfo(element));
        }
        List<NjkBlockName> overriding = NjkResolver.overridingBlocks(block);
        if (!overriding.isEmpty()) {
            result.add(NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod)
                    .setTargets(overriding)
                    .setTooltipText("Block '" + block.getName() + "' is overridden in " + overriding.size() + " template(s)")
                    .createLineMarkerInfo(element));
        }
    }
}
