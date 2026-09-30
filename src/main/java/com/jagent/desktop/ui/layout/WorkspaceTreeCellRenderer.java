package com.jagent.desktop.ui.layout;

import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.UiText;
import java.awt.Color;
import java.awt.Component;
import java.nio.file.Path;
import java.util.function.Function;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

public final class WorkspaceTreeCellRenderer extends DefaultTreeCellRenderer {
    private final Path workspace;
    private final Function<Path, String> statusForPath;

    public WorkspaceTreeCellRenderer(
            final Path workspace, final Function<Path, String> statusForPath) {
        super();
        this.workspace = workspace.toAbsolutePath().normalize();
        this.statusForPath = statusForPath;
    }

    @Override
    public Component getTreeCellRendererComponent(
            final JTree tree,
            final Object value,
            final boolean selected,
            final boolean expanded,
            final boolean leaf,
            final int row,
            final boolean focused) {
        final Component component =
                super.getTreeCellRendererComponent(
                        tree, value, selected, expanded, leaf, row, focused);
        final Object item = ((DefaultMutableTreeNode) value).getUserObject();
        if (item instanceof Path path) {
            final Path normalized = path.toAbsolutePath().normalize();
            final String status = statusCode(normalized);
            setText(
                    label(normalized)
                            + UiText.valueOrDefault(
                                    status == null ? null : " [" + status.trim() + "]", ""));
            setToolTipText(normalized.toString());
            if (!selected && status != null) {
                setForeground(statusColor(status));
            }
        } else if (WorkspaceTree.LOADING.equals(item)) {
            setText(WorkspaceTree.LOADING);
            setIcon(UiIcons.activity());
        }
        return component;
    }

    private String label(final Path path) {
        if (workspace.equals(path)) {
            final Path name = workspace.getFileName();
            return name == null ? workspace.toString() : name.toString();
        }
        final Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private String statusCode(final Path path) {
        final String code = statusForPath.apply(path);
        if (code == null || code.isBlank()) {
            return null;
        }
        return code;
    }

    private Color statusColor(final String code) {
        if (code.contains("D") || code.contains("U")) {
            return Theme.dangerColor();
        }
        if (code.contains("A") || code.contains("?")) {
            return Theme.successColor();
        }
        return Theme.warningColor();
    }
}
