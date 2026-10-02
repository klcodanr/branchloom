package com.jagent.desktop.ui.layout;

import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.UiText;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

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
    protected void paintComponent(final Graphics graphics) {
        final Graphics2D copy = (Graphics2D) graphics.create();
        try {
            if (isOpaque()) {
                copy.setColor(getBackground());
                copy.fillRect(0, 0, getWidth(), getHeight());
            }
            final Insets insets = getInsets();
            final Icon icon = getIcon();
            int textX = insets.left;
            if (icon != null) {
                final int iconY = (getHeight() - icon.getIconHeight()) / 2;
                icon.paintIcon(this, copy, textX, iconY);
                textX += icon.getIconWidth() + getIconTextGap();
            }
            final FontMetrics metrics = copy.getFontMetrics(getFont());
            final int textY =
                    insets.top
                            + (getHeight() - insets.top - insets.bottom - metrics.getHeight()) / 2
                            + metrics.getAscent();
            copy.setFont(getFont());
            copy.setColor(getForeground());
            copy.drawString(getText(), textX, textY);
        } finally {
            copy.dispose();
        }
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
                    label(tree, row, normalized)
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

    private String label(final JTree tree, final int row, final Path path) {
        if (workspace.equals(path)) {
            final Path name = workspace.getFileName();
            return name == null ? workspace.toString() : name.toString();
        }
        final Path parent = treeParentPath(tree, row);
        if (parent != null && sameRoot(parent, path)) {
            final Path relative = parent.relativize(path);
            if (relative.getNameCount() > 0) {
                return relative.toString().replace(java.io.File.separatorChar, '/');
            }
        }
        final Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private Path treeParentPath(final JTree tree, final int row) {
        final TreePath treePath = tree.getPathForRow(row);
        if (treePath == null || treePath.getPathCount() <= 1) {
            return null;
        }
        final Object parentComponent = treePath.getParentPath().getLastPathComponent();
        if (parentComponent instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof Path path) {
            return path.toAbsolutePath().normalize();
        }
        return null;
    }

    private static boolean sameRoot(final Path parent, final Path path) {
        final Path parentRoot = parent.getRoot();
        return parentRoot != null && parentRoot.equals(path.getRoot());
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
            Theme.Colors.danger();
        }
        if (code.contains("A") || code.contains("?")) {
            return Theme.Colors.success();
        }
        return Theme.Colors.warning();
    }
}
