package com.jagent.desktop.ui.components;

import com.formdev.flatlaf.ui.FlatLineBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/** Factory methods for common Swing components used by the application. */
public final class UiFactory {
    private UiFactory() {}

    public static JPanel panel() {
        return new JPanel();
    }

    public static Border pageBorder() {
        return new EmptyBorder(
                UiConstants.PAGE_MARGIN,
                UiConstants.PAGE_MARGIN,
                UiConstants.PAGE_MARGIN,
                UiConstants.PAGE_MARGIN);
    }

    public static Border sectionBorder() {
        return new EmptyBorder(
                UiConstants.SECTION_PADDING,
                UiConstants.SECTION_PADDING,
                UiConstants.SECTION_PADDING,
                UiConstants.SECTION_PADDING);
    }

    public static Border contentAreaBorder() {
        final Color borderColor = UIManager.getColor("Component.borderColor");
        final Insets padding =
                new Insets(
                        UiConstants.SPACING_SM,
                        UiConstants.SPACING_SM,
                        UiConstants.SPACING_SM,
                        UiConstants.SPACING_SM);
        return BorderFactory.createCompoundBorder(
                new EmptyBorder(
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS),
                new FlatLineBorder(padding, borderColor, 1f, 8));
    }

    public static Border cardBorder() {
        return new EmptyBorder(
                UiConstants.CARD_PADDING,
                UiConstants.CARD_PADDING,
                UiConstants.CARD_PADDING,
                UiConstants.CARD_PADDING);
    }

    public static JPanel verticalLayout() {
        final JPanel panel = panel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    public static JPanel verticalLayoutWithHeader(final Component header) {
        final JPanel panel = verticalLayout();
        panel.add(header);
        panel.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        return panel;
    }

    public static GridBagConstraints formConstraints() {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets =
                new Insets(
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        return constraints;
    }

    public static JLabel label(final String text, final Theme.FontSize size) {
        final JLabel label = new JLabel(text);
        label.setFont(Theme.font(size));
        return label;
    }

    public static JTextArea selectableText(final String text, final Theme.FontSize size) {
        final JTextArea area = new JTextArea(text == null ? "" : text);
        configureTextAreaTraversal(area);
        area.setFont(Theme.font(size));
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(1);
        area.setBorder(new EmptyBorder(0, 0, 0, 0));
        return area;
    }

    public static void configureTextAreaTraversal(final JTextArea area) {
        area.setFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                Set.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)));
        area.setFocusTraversalKeys(
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                Set.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)));
    }

    public static void configureDialogCloseOnEscape(final JDialog dialog) {
        dialog.getRootPane()
                .registerKeyboardAction(
                        event -> dialog.dispose(),
                        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                        JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public static JTextPane selectableHtml(final String html, final Theme.FontSize size) {
        final JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setText(html == null ? "" : html);
        pane.setFont(Theme.font(size));
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(new EmptyBorder(0, 0, 0, 0));
        pane.setMargin(UiConstants.ZERO_INSETS);
        return pane;
    }

    public static JPanel loading(final String text) {
        final JPanel loading = new JPanel(new GridBagLayout());
        loading.setOpaque(false);
        final JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(
                new EmptyBorder(
                        UiConstants.SPACING_XL,
                        UiConstants.SPACING_2XL,
                        UiConstants.SPACING_XL,
                        UiConstants.SPACING_2XL));
        final JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(180, 8));
        progress.setMaximumSize(new Dimension(180, 8));
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        final JLabel message = label(text, Theme.FontSize.MD);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(progress);
        content.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
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
        metric.add(Box.createVerticalStrut(UiConstants.SPACING_XS));
        metric.add(content);
        return metric;
    }

    public static JPanel form(final Object... items) {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(sectionBorder());
        final GridBagConstraints constraints = formConstraints();
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
        panel.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        final JLabel detailLabel = label(detail, Theme.FontSize.MD);
        detailLabel.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(detailLabel);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    public static JButton iconButton(final Icon icon) {
        final JButton button = new JButton(icon);
        button.setBorderPainted(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(UiConstants.ZERO_INSETS);
        button.setPreferredSize(new Dimension(22, 24));
        return button;
    }

    public static JButton link(final String text, final Runnable action) {
        final JButton link = new JButton(text);
        link.putClientProperty("JButton.buttonType", "borderless");
        link.setBorderPainted(false);
        link.setContentAreaFilled(true);
        link.setFocusable(true);
        link.setFocusPainted(true);
        link.setRolloverEnabled(true);
        link.setMargin(UiConstants.ZERO_INSETS);
        link.setHorizontalAlignment(JButton.LEFT);
        link.getAccessibleContext().setAccessibleName(text);
        link.addActionListener(event -> action.run());
        return link;
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
