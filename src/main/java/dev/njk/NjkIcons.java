package dev.njk;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

public final class NjkIcons {
    public static final Icon FILE = IconLoader.getIcon("/icons/nunjucks.svg", NjkIcons.class);
    public static final Icon RENDER_SITES = IconLoader.getIcon("/icons/renderSites.svg", NjkIcons.class);

    private NjkIcons() {
    }
}
