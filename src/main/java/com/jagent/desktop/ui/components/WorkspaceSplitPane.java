package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

/** Resizable workspace content with a hideable files panel. */
public final class WorkspaceSplitPane extends JSplitPane {
    private final Supplier<JPanel> workspace;

    public WorkspaceSplitPane(final JTabbedPane content, final Supplier<JPanel> workspace) {
        super(HORIZONTAL_SPLIT);
        this.workspace = workspace;
        setLeftComponent(content);
        setRightComponent(new WorkspaceSidePanel(workspace.get(), this::hideWorkspace));
        setResizeWeight(1.0);
        setContinuousLayout(true);
        setBorder(null);
        setDividerLocation(0.75);
    }

    public void showWorkspace() {
        setRightComponent(new WorkspaceSidePanel(workspace.get(), this::hideWorkspace));
        setDividerLocation(0.75);
    }

    private void hideWorkspace() {
        setRightComponent(null);
    }

    private static final class WorkspaceSidePanel extends JPanel {
        private WorkspaceSidePanel(final JPanel workspace, final Runnable close) {
            super(new BorderLayout(0, 8));
            setBorder(new EmptyBorder(0, 12, 0, 0));
            setMinimumSize(new Dimension(160, 0));
            final JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.setBorder(new EmptyBorder(0, 0, 0, 4));
            header.add(UiFactory.label("Files", Theme.FontSize.MD), BorderLayout.WEST);
            final JButton closeButton = UiFactory.iconButton(UiIcons.chevronRight());
            closeButton.setToolTipText("Hide files");
            closeButton.getAccessibleContext().setAccessibleName("Hide files");
            closeButton.addActionListener(event -> close.run());
            final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actions.setOpaque(false);
            actions.add(closeButton);
            header.add(actions, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
            add(workspace, BorderLayout.CENTER);
        }
    }
}
