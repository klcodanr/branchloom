package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** Shared presentation shell for editable settings lists. */
public final class SettingsEditorPanel extends JPanel {
    private static final int CONFIGURED_LIST_HEIGHT = 260;
    private static final EmptyBorder EDITOR_BORDER =
            new EmptyBorder(
                    UiConstants.CONTENT_PADDING,
                    UiConstants.CONTENT_PADDING,
                    UiConstants.CONTENT_PADDING,
                    UiConstants.CONTENT_PADDING);

    public SettingsEditorPanel(
            final String title,
            final String description,
            final JPanel rows,
            final JPanel headers,
            final String addLabel,
            final Runnable addRow) {
        super(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        setOpaque(false);
        setBorder(EDITOR_BORDER);

        final JPanel intro = new JPanel(new BorderLayout());
        intro.setOpaque(false);
        final JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(Theme.FontSize.LG));
        intro.add(titleLabel, BorderLayout.WEST);
        final JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(Theme.font(Theme.FontSize.SM));
        intro.add(descriptionLabel, BorderLayout.EAST);
        add(intro, BorderLayout.NORTH);

        final JPanel table = new JPanel(new BorderLayout());
        table.setOpaque(false);
        if (headers != null) {
            table.add(headers, BorderLayout.NORTH);
        }
        table.add(configuredList(rows), BorderLayout.CENTER);
        add(table, BorderLayout.CENTER);

        final JButton add = new BaseButton(addLabel);
        add.setHorizontalAlignment(SwingConstants.LEFT);
        add.addActionListener(
                event -> {
                    addRow.run();
                    rows.revalidate();
                    rows.repaint();
                });
        add(add, BorderLayout.SOUTH);
    }

    private static JScrollPane configuredList(final JPanel rows) {
        final JScrollPane scroll = new JScrollPane(rows);
        scroll.setPreferredSize(new Dimension(0, CONFIGURED_LIST_HEIGHT));
        scroll.setMinimumSize(new Dimension(0, CONFIGURED_LIST_HEIGHT));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }
}
