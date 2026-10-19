package com.jagent.desktop.ui.components;

import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/** Shared visual shell for compact cards shown in board and list views. */
public abstract class CardPanel extends JPanel {
    protected CardPanel() {
        super();
        super.setBackground(Theme.Colors.textareaBackground());
        super.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.Colors.border()), UiBorders.card()));
        super.setPreferredSize(
                new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        super.setMinimumSize(new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        super.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiConstants.PR_CARD_HEIGHT));
        super.setAlignmentX(LEFT_ALIGNMENT);
        super.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    protected final JLabel addNumber(final String text, final JPopupMenu popupMenu) {
        final JLabel number = new JLabel(text);
        number.setFont(Theme.boldFont(Theme.FontSize.XS));
        number.setForeground(Theme.Colors.muted());
        number.setAlignmentX(LEFT_ALIGNMENT);
        number.setComponentPopupMenu(popupMenu);
        add(number);
        return number;
    }

    protected final JLabel addTitle(final String text, final JPopupMenu popupMenu) {
        final JLabel title = new JLabel(text);
        title.setFont(Theme.boldFont(Theme.FontSize.MD));
        title.setForeground(Theme.Colors.foreground());
        title.setToolTipText(text);
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setComponentPopupMenu(popupMenu);
        add(title);
        return title;
    }

    protected final JPanel addMetadata(
            final StatusDot dot, final String text, final JPopupMenu popupMenu) {
        final JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(dot);
        row.add(Box.createHorizontalStrut(UiConstants.SPACING_XS));
        final JLabel metadata = new JLabel(text);
        metadata.setFont(Theme.font(Theme.FontSize.XS));
        metadata.setForeground(Theme.Colors.muted());
        metadata.setToolTipText(text);
        row.add(metadata);
        row.setComponentPopupMenu(popupMenu);
        add(row);
        return row;
    }
}
