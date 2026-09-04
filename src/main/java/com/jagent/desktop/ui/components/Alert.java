package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/** Displays a bordered message with an action. */
public final class Alert extends JPanel {

    public Alert(final Content content) {
        super(new BorderLayout());
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(content.color(), 2),
                        new EmptyBorder(
                                UiConstants.CARD_PADDING,
                                UiConstants.COMPONENT_GAP,
                                UiConstants.CARD_PADDING,
                                UiConstants.COMPONENT_GAP)));

        final JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        final JTextArea text = UiFactory.selectableText(content.text(), Theme.FontSize.MD);
        text.setFocusable(false);
        text.setRows(2);
        text.setColumns(40);
        text.setAlignmentX(LEFT_ALIGNMENT);
        body.add(text);
        body.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        final JButton button = UiFactory.button(content.actionLabel());
        button.addActionListener(event -> content.action().run());
        button.setAlignmentX(LEFT_ALIGNMENT);
        body.add(button);
        add(body, BorderLayout.CENTER);
    }

    public record Content(String text, Color color, String actionLabel, Runnable action) {}
}
