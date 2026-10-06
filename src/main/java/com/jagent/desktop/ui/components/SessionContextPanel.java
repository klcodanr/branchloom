package com.jagent.desktop.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

/** Agent-context file and editor controls for a session summary. */
public final class SessionContextPanel extends JPanel {
    private final JTextArea file =
            new SelectableTextLabel("Loading context file...", Theme.FontSize.MD);
    private final JTextArea content = new BaseTextArea("Loading context...");
    private final JButton save = new BaseButton("Save");

    public SessionContextPanel(final Runnable saveContext) {
        super(new BorderLayout(0, UiConstants.SPACING_XS));
        setOpaque(false);
        file.setRows(1);
        content.setRows(10);
        content.setEditable(false);
        save.setEnabled(false);
        save.setFont(Theme.font(Theme.FontSize.XS));
        save.setMargin(UiConstants.ZERO_INSETS);
        save.addActionListener(event -> saveContext.run());
    }

    public JTextArea file() {
        return file;
    }

    public JTextArea content() {
        return content;
    }

    public void showLoading() {
        file.setEditable(false);
        file.setText("Loading context file...");
        file.setCaretPosition(0);
        content.setText("Loading context...");
        content.setCaretPosition(0);
        save.setEnabled(false);
    }

    public void show(final Path path, final String value) {
        file.setText(path == null ? "Not configured" : path.toString());
        file.setCaretPosition(0);
        content.setText(UiText.valueOrDefault(value, "No context configured."));
        content.setCaretPosition(0);
        content.setEditable(path != null);
        save.setEnabled(path != null);
    }

    public JPanel contentView() {
        final JPanel view = new JPanel(new BorderLayout(0, UiConstants.SPACING_XS));
        view.setOpaque(false);
        view.add(file, BorderLayout.NORTH);
        view.add(contentScroll(), BorderLayout.CENTER);
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(save);
        view.add(actions, BorderLayout.SOUTH);
        return view;
    }

    private JScrollPane contentScroll() {
        final JScrollPane scroll = new JScrollPane(content);
        scroll.setPreferredSize(new Dimension(0, 180));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }
}
