package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.IllegalComponentStateException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.junit.jupiter.api.Test;

class UiPopupMenusTest {
    @Test
    void selectsFirstEnabledItemAndHandlesCancellation() {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem disabled = new JMenuItem("Disabled");
        disabled.setEnabled(false);
        menu.add(disabled);
        final JMenuItem enabled = new JMenuItem("Enabled");
        menu.add(enabled);
        final JButton invoker = new JButton();

        assertThrows(
                IllegalComponentStateException.class, () -> UiPopupMenus.show(menu, invoker, 0, 0));
        final PopupMenuEvent event = new PopupMenuEvent(menu);
        for (final PopupMenuListener listener : menu.getPopupMenuListeners().clone()) {
            listener.popupMenuWillBecomeVisible(event);
        }
        assertArrayEquals(
                new MenuElement[] {menu, enabled},
                MenuSelectionManager.defaultManager().getSelectedPath(),
                "first enabled item should be selected");
        for (final PopupMenuListener listener : menu.getPopupMenuListeners()) {
            listener.popupMenuCanceled(event);
        }
    }

    @Test
    void reportsPopupVisibilityChanges() {
        final JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem("Item"));
        final JButton invoker = new JButton();
        menu.setInvoker(invoker);
        final List<Boolean> visibility = new ArrayList<>();

        UiPopupMenus.install(menu, invoker, visibility::add);
        final PopupMenuEvent event = new PopupMenuEvent(menu);

        for (final PopupMenuListener listener : menu.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(event);
            listener.popupMenuCanceled(event);
            listener.popupMenuWillBecomeInvisible(event);
        }

        assertEquals(
                List.of(true, false, false), visibility, "visibility changes should be reported");
    }
}
