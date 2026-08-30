package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.BooleanSupplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class SettingsPanel {
    private SettingsPanel() {}

    public static JPanel render(
            final String title,
            final String description,
            final JComponent form,
            final Runnable save,
            final Runnable cancel,
            final BooleanSupplier dirty) {
        final JPanel screen = UiFactory.panel();
        screen.setLayout(new BorderLayout(0, 20));
        screen.setBorder(new EmptyBorder(4, 4, 4, 4));
        final JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(Box.createVerticalStrut(7));
        final JLabel titleLabel = UiFactory.label(title, Theme.FontSize.XXL);
        titleLabel.setFont(Theme.boldFont(Theme.FontSize.XXL));
        heading.add(titleLabel);
        heading.add(Box.createVerticalStrut(7));
        if (!description.isBlank()) {
            heading.add(UiFactory.label(description, Theme.FontSize.MD));
        }
        screen.add(heading, BorderLayout.NORTH);
        final JPanel body = UiFactory.panel();
        body.setLayout(new BorderLayout());
        body.add(form, BorderLayout.CENTER);
        final JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(14);
        screen.add(bodyScroll, BorderLayout.CENTER);
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(3, 0, 3, 0));
        actions.setPreferredSize(new Dimension(0, 42));
        actions.setMinimumSize(new Dimension(0, 42));
        final JButton saveButton = UiFactory.button("Save");
        saveButton.setBackground(UIManager.getColor("Component.focusColor"));
        saveButton.setOpaque(true);
        saveButton.setFont(Theme.boldFont(Theme.FontSize.MD));
        saveButton.addActionListener(e -> save.run());
        final JButton cancelButton = UiFactory.button("Cancel");
        cancelButton.addActionListener(
                e -> {
                    if (!dirty.getAsBoolean()
                            || JOptionPane.showConfirmDialog(
                                            screen,
                                            "Discard unsaved changes?",
                                            "Unsaved changes",
                                            JOptionPane.YES_NO_OPTION,
                                            JOptionPane.WARNING_MESSAGE)
                                    == JOptionPane.YES_OPTION) {
                        cancel.run();
                    }
                });
        actions.add(cancelButton);
        actions.add(saveButton);
        screen.add(actions, BorderLayout.SOUTH);
        return screen;
    }

    public static JPanel labeledField(final String title, final JComponent field) {
        final JPanel group = new JPanel(new BorderLayout(0, 6));
        group.setOpaque(false);
        group.add(UiFactory.label(title, Theme.FontSize.MD), BorderLayout.NORTH);
        if (field instanceof JTextArea area) {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            final JScrollPane areaScroll = new JScrollPane(area);
            group.add(areaScroll, BorderLayout.CENTER);
            return group;
        }
        group.add(field, BorderLayout.CENTER);
        return group;
    }
}
