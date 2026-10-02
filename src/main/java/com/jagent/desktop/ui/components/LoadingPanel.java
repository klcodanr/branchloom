package com.jagent.desktop.ui.components;

import java.awt.Dimension;
import java.awt.GridBagLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;

/** Centered loading panel for page-sized content. */
public final class LoadingPanel extends JPanel {
    public LoadingPanel(final String text) {
        super(new GridBagLayout());

        setOpaque(false);
        final JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(
                new EmptyBorder(
                        UiConstants.SPACING_XL,
                        UiConstants.SPACING_2XL,
                        UiConstants.SPACING_XL,
                        UiConstants.SPACING_2XL));
        final JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(180, 8));
        progress.setMaximumSize(new Dimension(180, 8));
        progress.setAlignmentX(CENTER_ALIGNMENT);
        final JLabel message = new JLabel(text);
        message.setFont(Theme.font(Theme.FontSize.MD));
        message.setAlignmentX(CENTER_ALIGNMENT);
        content.add(progress);
        content.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        content.add(message);
        add(content);
    }
}
