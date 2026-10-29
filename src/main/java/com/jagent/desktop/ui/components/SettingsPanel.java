package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.dialog.DialogService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.BooleanSupplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

public final class SettingsPanel {
    private SettingsPanel() {}

    public static JPanel render(
            final String title,
            final String description,
            final JComponent form,
            final Runnable save,
            final Runnable cancel,
            final BooleanSupplier dirty,
            final DialogService dialogService) {
        final JPanel screen = new JPanel();
        screen.setLayout(new BorderLayout(0, UiConstants.SPACING_XL));
        screen.setBorder(
                new EmptyBorder(
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING));
        if (!title.isBlank() || !description.isBlank()) {
            final JPanel heading = new JPanel();
            heading.setOpaque(false);
            heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
            heading.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
            final JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(Theme.font(Theme.FontSize.XXL));
            titleLabel.setFont(Theme.boldFont(Theme.FontSize.XXL));
            heading.add(titleLabel);
            heading.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
            if (!description.isBlank()) {
                final JLabel descriptionLabel = new JLabel(description);
                descriptionLabel.setFont(Theme.font(Theme.FontSize.MD));
                heading.add(descriptionLabel);
            }
            screen.add(heading, BorderLayout.NORTH);
        }
        final JPanel body = new JPanel();
        body.setLayout(new BorderLayout());
        body.add(form, BorderLayout.CENTER);
        final JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setOpaque(false);
        bodyScroll.setBorder(null);
        bodyScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(14);
        screen.add(bodyScroll, BorderLayout.CENTER);
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(UiConstants.SPACING_XS, 0, UiConstants.SPACING_XS, 0));
        actions.setPreferredSize(new Dimension(0, 42));
        actions.setMinimumSize(new Dimension(0, 42));
        final JButton saveButton = new BaseButton("Save");
        saveButton.setBackground(Theme.Colors.focus());
        saveButton.setOpaque(true);
        saveButton.setFont(Theme.boldFont(Theme.FontSize.MD));
        saveButton.addActionListener(e -> save.run());
        final JButton cancelButton = new BaseButton("Cancel");
        cancelButton.addActionListener(
                e -> {
                    if (!dirty.getAsBoolean()
                            || dialogService.confirm(
                                            "Discard unsaved changes?",
                                            "Unsaved changes",
                                            DialogService.OptionType.YES_NO,
                                            DialogService.MessageType.WARNING)
                                    == DialogService.ReturnType.OK) {
                        cancel.run();
                    }
                });
        actions.add(cancelButton);
        actions.add(saveButton);
        screen.add(actions, BorderLayout.SOUTH);
        return screen;
    }
}
