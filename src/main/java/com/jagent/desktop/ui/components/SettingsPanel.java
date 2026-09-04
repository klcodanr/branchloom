package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
            final JLabel titleLabel = UiFactory.label(title, Theme.FontSize.XXL);
            titleLabel.setFont(Theme.boldFont(Theme.FontSize.XXL));
            heading.add(titleLabel);
            heading.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
            if (!description.isBlank()) {
                heading.add(UiFactory.label(description, Theme.FontSize.MD));
            }
            screen.add(heading, BorderLayout.NORTH);
        }
        final JPanel body = UiFactory.panel();
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
        final JPanel group = new JPanel(new GridBagLayout());
        group.setOpaque(false);
        final GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets =
                new Insets(0, 0, UiConstants.CONTENT_PADDING, UiConstants.COMPONENT_GAP);
        final JLabel label = UiFactory.label(title, Theme.FontSize.MD);
        label.setPreferredSize(new Dimension(180, label.getPreferredSize().height));
        group.add(label, labelConstraints);
        final GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1;
        fieldConstraints.gridwidth = GridBagConstraints.REMAINDER;
        fieldConstraints.insets = new Insets(0, 0, UiConstants.CONTENT_PADDING, 0);
        if (field instanceof JTextArea area) {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            final JScrollPane areaScroll = new JScrollPane(area);
            fieldConstraints.fill = GridBagConstraints.BOTH;
            fieldConstraints.weighty = 1;
            group.add(areaScroll, fieldConstraints);
            return group;
        }
        group.add(field, fieldConstraints);
        return group;
    }
}
