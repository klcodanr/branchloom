package com.jagent.desktop.ui.components;

import java.awt.Component;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/** Popup-menu selection and focus restoration behavior. */
public final class UiPopupMenus {
    private UiPopupMenus() {}

    public static void install(final JPopupMenu menu, final Component invoker) {
        install(menu, invoker, ignored -> {});
    }

    public static void install(
            final JPopupMenu menu,
            final Component invoker,
            final Consumer<Boolean> visibilityChanged) {
        menu.addPopupMenuListener(
                new PopupMenuListener() {
                    @Override
                    public void popupMenuWillBecomeVisible(final PopupMenuEvent event) {
                        visibilityChanged.accept(true);
                        if (menu.getInvoker() == null) {
                            return;
                        }
                        for (final Component component : menu.getComponents()) {
                            if (component instanceof JMenuItem item && item.isEnabled()) {
                                MenuSelectionManager.defaultManager()
                                        .setSelectedPath(new MenuElement[] {menu, item});
                                return;
                            }
                        }
                    }

                    @Override
                    public void popupMenuCanceled(final PopupMenuEvent event) {
                        visibilityChanged.accept(false);
                        MenuSelectionManager.defaultManager().clearSelectedPath();
                        restoreFocus();
                    }

                    @Override
                    public void popupMenuWillBecomeInvisible(final PopupMenuEvent event) {
                        visibilityChanged.accept(false);
                        MenuSelectionManager.defaultManager().clearSelectedPath();
                        restoreFocus();
                        SwingUtilities.invokeLater(() -> menu.removePopupMenuListener(this));
                    }

                    private void restoreFocus() {
                        SwingUtilities.invokeLater(invoker::requestFocusInWindow);
                    }
                });
    }

    public static void show(
            final JPopupMenu menu, final Component invoker, final int x, final int y) {
        install(menu, invoker);
        menu.show(invoker, x, y);
    }
}
