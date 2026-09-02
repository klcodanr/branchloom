package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
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
                        BorderFactory.createLineBorder(Theme.successColor(), 2),
                        new EmptyBorder(12, 14, 12, 14)));

        final JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        final JTextArea text = new JTextArea(content.text());
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setRows(2);
        text.setColumns(40);
        text.setOpaque(false);
        text.setBorder(null);
        text.setAlignmentX(LEFT_ALIGNMENT);
        body.add(text);
        body.add(Box.createVerticalStrut(8));
        final JButton button = UiFactory.button("Remove session and worktree");
        button.addActionListener(event -> content.action().run());
        button.setAlignmentX(LEFT_ALIGNMENT);
        body.add(button);
        add(body, BorderLayout.CENTER);
    }

    public record Content(String text, Runnable action) {}
}
