package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.IllegalComponentStateException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.junit.jupiter.api.Test;

class UiFactoryTest {
    private static final String VALUE_MESSAGE = "factory value should match";
    private static final String CONDITION_MESSAGE = "factory condition should hold";

    @Test
    void createsConfiguredTextAndButtons() {
        final var text = UiFactory.selectableText(null, Theme.FontSize.MD);
        final JButton button = UiFactory.button("Save");
        final JButton iconButton = UiFactory.iconButton(new UiFactory.MenuIcon(Color.BLUE));

        assertEquals("", text.getText(), VALUE_MESSAGE);
        assertFalse(text.isEditable(), CONDITION_MESSAGE);
        assertEquals("Save", button.getAccessibleContext().getAccessibleName(), VALUE_MESSAGE);
        assertEquals(22, iconButton.getPreferredSize().width, VALUE_MESSAGE);
    }

    @Test
    void buildsLoadingMetricsFormsAndEmptyPanels() {
        final var loading = UiFactory.loading("Loading");
        final var inline = UiFactory.inlineLoading("Wait");
        final var metric = UiFactory.metric("Count", "4");
        final var form = UiFactory.form("Name", new JTextArea());
        final var empty = UiFactory.empty("Nothing", "Try again");

        final JProgressBar progress =
                (JProgressBar) ((javax.swing.JPanel) loading.getComponent(0)).getComponent(0);
        assertTrue(progress.isIndeterminate(), CONDITION_MESSAGE);
        assertEquals(180, progress.getPreferredSize().width, VALUE_MESSAGE);
        assertEquals(8, progress.getPreferredSize().height, VALUE_MESSAGE);
        assertEquals(
                13,
                ((JLabel) ((javax.swing.JPanel) loading.getComponent(0)).getComponent(2))
                        .getFont()
                        .getSize(),
                VALUE_MESSAGE);
        assertEquals(2, inline.getComponentCount(), VALUE_MESSAGE);
        assertEquals(3, metric.getComponentCount(), VALUE_MESSAGE);
        assertEquals(2, form.getComponentCount(), VALUE_MESSAGE);
        assertEquals(5, empty.getComponentCount(), VALUE_MESSAGE);
        assertEquals("Nothing", ((JLabel) empty.getComponent(1)).getText(), VALUE_MESSAGE);
    }

    @Test
    void popupMenuSelectsFirstEnabledItemAndRestoresFocus() {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem disabled = new JMenuItem("Disabled");
        disabled.setEnabled(false);
        menu.add(disabled);
        final JMenuItem enabled = new JMenuItem("Enabled");
        menu.add(enabled);
        final JButton invoker = new JButton();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalComponentStateException.class,
                () -> UiFactory.showPopupMenu(menu, invoker, 0, 0));
        final PopupMenuEvent event = new PopupMenuEvent(menu);
        for (final PopupMenuListener listener : menu.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(event);
        }
        assertArrayEquals(
                new MenuElement[] {menu, enabled},
                MenuSelectionManager.defaultManager().getSelectedPath(),
                "first enabled popup item should receive focus");
        for (final PopupMenuListener listener : menu.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeInvisible(event);
        }
    }

    @Test
    void popupMenuHandlesMenusWithoutEnabledItemsAndCancellation() {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem disabled = new JMenuItem("Disabled");
        disabled.setEnabled(false);
        menu.add(disabled);
        final JButton invoker = new JButton();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalComponentStateException.class,
                () -> UiFactory.showPopupMenu(menu, invoker, 0, 0));
        final PopupMenuEvent event = new PopupMenuEvent(menu);
        for (final PopupMenuListener listener : menu.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(event);
            listener.popupMenuCanceled(event);
        }
    }
}
