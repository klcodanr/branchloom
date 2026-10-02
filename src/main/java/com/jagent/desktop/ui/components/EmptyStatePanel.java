package com.jagent.desktop.ui.components;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Empty-state presentation for content areas. */
public final class EmptyStatePanel extends JPanel {
    public EmptyStatePanel(final String title, final String detail) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(Box.createVerticalGlue());

        final JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(Theme.FontSize.XXL));
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(titleLabel);
        add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        final JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(Theme.font(Theme.FontSize.MD));
        detailLabel.setForeground(Theme.Colors.muted());
        detailLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(detailLabel);
        add(Box.createVerticalGlue());
    }
}
