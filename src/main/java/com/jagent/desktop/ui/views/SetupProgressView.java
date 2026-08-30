package com.jagent.desktop.ui.views;

import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class SetupProgressView extends JPanel {
    private final JPanel steps = new JPanel();
    private final List<JLabel> statuses = new ArrayList<>();
    private final JLabel output = UiFactory.label("", Theme.FontSize.SM);
    private final JPanel footer = new JPanel(new BorderLayout(0, 8));
    private JPanel actions;
    private boolean retryAdded;

    public SetupProgressView(final String sessionName, final List<String> commands) {
        this(sessionName, commands, true);
    }

    public SetupProgressView(
            final String sessionName, final List<String> commands, final boolean createWorktree) {
        this(sessionName, commands, createWorktree, createWorktree);
    }

    public SetupProgressView(
            final String sessionName,
            final List<String> commands,
            final boolean createWorktree,
            final boolean startAgent) {
        super();
        setLayout(new BorderLayout(0, 20));

        final JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(UiFactory.label("Starting session", Theme.FontSize.XXL));
        heading.add(UiFactory.label(sessionName, Theme.FontSize.LG));
        add(heading, BorderLayout.NORTH);

        steps.setOpaque(false);
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        if (createWorktree) {
            addStep("Create worktree");
        }
        for (final String command : commands) {
            addStep("Run setup: " + command);
        }
        if (startAgent) {
            addStep("Start agent");
        }
        add(steps, BorderLayout.NORTH);
        output.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        footer.setOpaque(false);
        footer.add(output, BorderLayout.NORTH);
        add(footer, BorderLayout.SOUTH);
    }

    public void start(final int index) {
        retryAdded = false;
        update(index, "Running", UIManager.getColor("Component.focusColor"));
    }

    public void complete(final int index) {
        update(index, "Complete", UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
    }

    public void fail(final int index) {
        update(index, "Failed", Theme.dangerColor());
    }

    public void output(final String line) {
        String value = line == null ? "" : line.strip();
        if (value.length() > 180) {
            value = value.substring(value.length() - 180);
        }
        output.setText(value);
    }

    public void failureActions(final Runnable retry, Runnable cancel) {
        if (retryAdded) {
            return;
        }
        retryAdded = true;
        if (actions == null) {
            actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            footer.add(actions, BorderLayout.SOUTH);
        }
        final JButton retryButton = UiFactory.button("Retry");
        retryButton.addActionListener(
                event -> {
                    retryButton.setVisible(false);
                    retry.run();
                });
        actions.add(retryButton);
        revalidate();
    }

    private void addStep(final String text) {
        final JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 0, 8, 0));
        row.add(UiFactory.label(text, Theme.FontSize.LG), BorderLayout.CENTER);
        final JLabel status = UiFactory.label("Pending", Theme.FontSize.SM);
        status.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        row.add(status, BorderLayout.EAST);
        statuses.add(status);
        steps.add(row);
    }

    private void update(final int index, final String text, final java.awt.Color color) {
        if (index < 0 || index >= statuses.size()) {
            return;
        }
        statuses.get(index).setText(text);
        statuses.get(index).setForeground(color);
        revalidate();
        repaint();
    }
}
