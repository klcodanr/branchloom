package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Toolbar for switching file views and searching loaded file content. */
public final class FileViewerToolbar extends JPanel {
    private final JLabel status = new JLabel("Loading...");

    public FileViewerToolbar(
            final Path workspace,
            final Path file,
            final boolean showDiffInitially,
            final FileSearchControls searchControls,
            final Runnable showSource,
            final Runnable showDiff) {
        super(new BorderLayout(UiConstants.CONTENT_PADDING, 0));
        setOpaque(false);

        final JLabel path = new JLabel(workspace.relativize(file).toString());
        path.setFont(Theme.font(Theme.FontSize.SM));
        path.setToolTipText(file.toString());
        add(path, BorderLayout.WEST);

        final JPanel controls =
                new JPanel(new FlowLayout(FlowLayout.RIGHT, UiConstants.SPACING_XS, 0));
        controls.setOpaque(false);
        final JPanel viewModes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        viewModes.setOpaque(false);
        final JToggleButton sourceButton = segmentedButton(UiIcons.fileCode(), "File", "first");
        final JToggleButton diffButton = segmentedButton(UiIcons.gitCompare(), "Diff", "last");
        final ButtonGroup group = new ButtonGroup();
        group.add(sourceButton);
        group.add(diffButton);
        sourceButton.setSelected(!showDiffInitially);
        diffButton.setSelected(showDiffInitially);
        sourceButton.addActionListener(event -> showSource.run());
        diffButton.addActionListener(event -> showDiff.run());
        viewModes.add(sourceButton);
        viewModes.add(diffButton);
        controls.add(viewModes);
        controls.add(searchControls);
        status.setFont(Theme.font(Theme.FontSize.XS));
        controls.add(status);
        add(controls, BorderLayout.EAST);
    }

    public void setStatus(final String text) {
        status.setText(text);
    }

    private static JToggleButton segmentedButton(
            final Icon icon, final String name, final String position) {
        final JToggleButton button = new JToggleButton(icon);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        button.putClientProperty("JButton.buttonType", "segmented");
        button.putClientProperty("JButton.segmentPosition", position);
        return button;
    }
}
