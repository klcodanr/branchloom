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
        menu.setInvoker(invoker);

        UiPopupMenus.install(menu, invoker);
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

    @Test
    void leavesSelectionEmptyWhenThereAreNoEnabledItemsOrInvoker() {
        final JPopupMenu noEnabledItems = new JPopupMenu();
        final JMenuItem disabled = new JMenuItem("Disabled");
        disabled.setEnabled(false);
        noEnabledItems.add(disabled);
        final JButton invoker = new JButton();
        noEnabledItems.setInvoker(invoker);
        UiPopupMenus.install(noEnabledItems, invoker);
        final PopupMenuEvent enabledEvent = new PopupMenuEvent(noEnabledItems);
        for (final PopupMenuListener listener : noEnabledItems.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(enabledEvent);
        }
        assertEquals(
                0,
                MenuSelectionManager.defaultManager().getSelectedPath().length,
                "a menu without enabled items should not select an item");

        final JPopupMenu noInvoker = new JPopupMenu();
        noInvoker.add(new JMenuItem("Item"));
        UiPopupMenus.install(noInvoker, invoker);
        final PopupMenuEvent noInvokerEvent = new PopupMenuEvent(noInvoker);
        for (final PopupMenuListener listener : noInvoker.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(noInvokerEvent);
        }
        assertEquals(
                0,
                MenuSelectionManager.defaultManager().getSelectedPath().length,
                "a menu without an invoker should not select an item");
    }

    @Test
    void installsBeforeShowingMenu() {
        final JPopupMenu menu = new JPopupMenu();
        final JButton invoker = new JButton();

        assertThrows(
                IllegalComponentStateException.class,
                () -> UiPopupMenus.show(menu, invoker, 0, 0),
                "show should delegate to the popup menu after installing behavior");
        assertEquals(2, menu.getPopupMenuListeners().length, "show should install a listener");
    }
}
