package com.jagent.desktop.ui.components;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;

public final class ClosableTabHeader {
    private ClosableTabHeader() {}

    public static JPanel create(
            final JTabbedPane tabs,
            final String title,
            final Component component,
            final Runnable dispose) {
        return create(tabs, title, component, dispose, null);
    }

    public static JPanel create(
            final JTabbedPane tabs,
            final String title,
            final Component component,
            final Runnable dispose,
            final Consumer<String> rename) {
        return create(tabs, title, component, dispose, rename, null);
    }

    public static JPanel create(
            final JTabbedPane tabs,
            final String title,
            final Component component,
            final Runnable dispose,
            final Consumer<String> rename,
            final JComponent status) {
        final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        header.setOpaque(false);
        if (component instanceof JComponent tab) {
            installCloseHandler(tabs, tab, dispose);
        }
        if (status != null) {
            header.add(status);
        }
        final JLabel titleLabel = UiFactory.label(title, Theme.FontSize.SM);
        header.add(titleLabel);
        installSelectionHandler(tabs, header, titleLabel);
        if (rename != null) {
            installRenameHandler(header, titleLabel, rename);
        }
        return header;
    }

    private static void installCloseHandler(
            final JTabbedPane tabs, final JComponent tab, final Runnable dispose) {
        tab.putClientProperty("JTabbedPane.tabClosable", true);
        tab.putClientProperty(
                "JTabbedPane.tabCloseCallback",
                (java.util.function.IntConsumer)
                        index -> {
                            if (index < 0 || index >= tabs.getTabCount()) {
                                return;
                            }
                            tabs.removeTabAt(index);
                            dispose.run();
                        });
    }

    private static void installSelectionHandler(
            final JTabbedPane tabs, final JPanel header, final JLabel titleLabel) {
        final MouseAdapter selectTab =
                new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent event) {
                        if (event.getButton() != MouseEvent.BUTTON1) {
                            return;
                        }
                        final int index = tabs.indexOfTabComponent(header);
                        if (index >= 0) {
                            tabs.setSelectedIndex(index);
                        }
                    }
                };
        header.addMouseListener(selectTab);
        titleLabel.addMouseListener(selectTab);
    }

    private static void installRenameHandler(
            final JPanel header, final JLabel titleLabel, final Consumer<String> rename) {
        final Runnable renameAction =
                () -> {
                    final String updated =
                            (String)
                                    JOptionPane.showInputDialog(
                                            header,
                                            "Tab name:",
                                            "Rename tab",
                                            JOptionPane.PLAIN_MESSAGE,
                                            null,
                                            null,
                                            titleLabel.getText());
                    if (updated == null || updated.isBlank()) {
                        return;
                    }
                    final String name = updated.trim();
                    titleLabel.setText(name);
                    rename.accept(name);
                };
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem renameItem = new JMenuItem("Rename tab...");
        renameItem.addActionListener(event -> renameAction.run());
        menu.add(renameItem);
        header.setComponentPopupMenu(menu);
        final MouseAdapter doubleClick =
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                            renameAction.run();
                        }
                    }
                };
        header.addMouseListener(doubleClick);
        titleLabel.addMouseListener(doubleClick);
    }
}
