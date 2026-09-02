package com.jagent.desktop.ui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/** Factory methods for common Swing components used by the application. */
public final class UiFactory {
    private UiFactory() {}

    public static JPanel panel() {
        return new JPanel();
    }

    public static JLabel label(final String text, final Theme.FontSize size) {
        final JLabel label = new JLabel(text);
        label.setFont(Theme.font(size));
        return label;
    }

    public static JTextArea selectableText(final String text, final Theme.FontSize size) {
        final JTextArea area = new JTextArea(text == null ? "" : text);
        area.setFont(Theme.font(size));
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(1);
        area.setBorder(new EmptyBorder(0, 0, 0, 0));
        return area;
    }

    public static JTextPane selectableHtml(final String html, final Theme.FontSize size) {
        final JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setText(html == null ? "" : html);
        pane.setFont(Theme.font(size));
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(new EmptyBorder(0, 0, 0, 0));
        pane.setMargin(new Insets(0, 0, 0, 0));
        return pane;
    }

    public static JPanel loading(final String text) {
        final JPanel loading = new JPanel(new GridBagLayout());
        loading.setOpaque(false);
        final JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 24, 20, 24));
        final JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(180, 8));
        progress.setMaximumSize(new Dimension(180, 8));
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        final JLabel message = label(text, Theme.FontSize.MD);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(progress);
        content.add(Box.createVerticalStrut(12));
        content.add(message);
        loading.add(content);
        return loading;
    }

    public static JPanel inlineLoading(final String text) {
        final JPanel loading = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        loading.setOpaque(false);
        final JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(16, 16));
        progress.setBorderPainted(false);
        loading.add(progress);
        loading.add(label(text, Theme.FontSize.SM));
        return loading;
    }

    public static JButton button(final String text) {
        final JButton button = new JButton(text);
        button.getAccessibleContext().setAccessibleName(text);
        button.setBorderPainted(false);
        return button;
    }

    public static JButton button(final String text, final Icon icon) {
        final JButton button = button(text);
        button.setIcon(icon);
        return button;
    }

    public static JPanel metric(final String name, final String value) {
        final JPanel metric = new JPanel();
        metric.setOpaque(false);
        metric.setLayout(new BoxLayout(metric, BoxLayout.Y_AXIS));
        final JLabel title = label(name, Theme.FontSize.XS);
        title.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        final JTextArea content = selectableText(value, Theme.FontSize.MD);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        metric.add(title);
        metric.add(Box.createVerticalStrut(4));
        metric.add(content);
        return metric;
    }

    public static JPanel form(final Object... items) {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < items.length; i += 2) {
            constraints.gridy = i / 2;
            constraints.gridx = 0;
            constraints.weightx = 0;
            panel.add(new JLabel(items[i].toString()), constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            panel.add((Component) items[i + 1], constraints);
        }
        return panel;
    }

    public static JPanel empty(final String title, final String detail) {
        final JPanel panel = panel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(Box.createVerticalGlue());
        final JLabel titleLabel = label(title, Theme.FontSize.XXL);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(8));
        final JLabel detailLabel = label(detail, Theme.FontSize.MD);
        detailLabel.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(detailLabel);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    public static JButton iconButton(final Icon icon) {
        final JButton button = new JButton(icon);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(22, 24));
        return button;
    }

    public static final class MenuIcon implements Icon {
        private final Color color;

        public MenuIcon(final Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(
                final Component component, final Graphics graphics, final int x, final int y) {
            graphics.setColor(color);
            graphics.fillRect(x + 2, y + 3, 10, 2);
            graphics.fillRect(x + 2, y + 7, 10, 2);
            graphics.fillRect(x + 2, y + 11, 10, 2);
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
