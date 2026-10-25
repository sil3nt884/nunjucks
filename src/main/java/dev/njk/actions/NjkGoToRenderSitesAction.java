package dev.njk.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import dev.njk.NjkFileType;
import dev.njk.resolve.NjkRenderSites;
import dev.njk.resolve.NjkTemplatePaths;
import org.jetbrains.annotations.NotNull;

import javax.swing.JList;
import java.util.List;

/** Right-click a template → Go to Render Sites: the render() calls and templates that render it. */
public final class NjkGoToRenderSitesAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(e.getProject() != null && file != null && !file.isDirectory()
                && file.getFileType() == NjkFileType.INSTANCE);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || file == null) return;

        List<NjkRenderSites.Site> sites = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> ReadAction.compute(() -> {
                    var template = NjkTemplatePaths.njkFile(project, file);
                    return template != null ? NjkRenderSites.find(template) : List.<NjkRenderSites.Site>of();
                }),
                "Finding where " + file.getName() + " is rendered", true, project);

        if (sites.isEmpty()) {
            JBPopupFactory.getInstance()
                    .createMessage("No render() calls or templates using " + file.getName() + " were found")
                    .showInBestPositionFor(e.getDataContext());
        } else if (sites.size() == 1) {
            navigate(project, sites.get(0));
        } else {
            JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(sites)
                    .setTitle("Where " + file.getName() + " Is Rendered")
                    .setRenderer(new SiteRenderer())
                    .setItemChosenCallback(site -> navigate(project, site))
                    .setNamerForFiltering(site -> site.file().getName() + " " + site.preview())
                    .createPopup()
                    .showInBestPositionFor(e.getDataContext());
        }
    }

    private static void navigate(Project project, NjkRenderSites.Site site) {
        VirtualFile vf = site.file().getVirtualFile();
        if (vf != null) new OpenFileDescriptor(project, vf, site.offset()).navigate(true);
    }

    private static final class SiteRenderer extends ColoredListCellRenderer<NjkRenderSites.Site> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends NjkRenderSites.Site> list, NjkRenderSites.Site site,
                                             int index, boolean selected, boolean hasFocus) {
            setIcon(site.file().getIcon(0));
            append(site.file().getName() + ":" + site.line(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append("  " + site.kind().description, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
            append("  " + site.preview(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }
}
