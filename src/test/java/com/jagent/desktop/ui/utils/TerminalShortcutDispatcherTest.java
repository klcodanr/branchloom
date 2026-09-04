package com.jagent.desktop.ui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class TerminalShortcutDispatcherTest {
    private static final int SHORTCUT_MODIFIERS = InputEvent.CTRL_DOWN_MASK;

    @Test
    void forwardsEveryMappedTerminalShortcutAndConsumesIt() {
        final InputMap inputMap = new InputMap();
        final ActionMap actionMap = new ActionMap();
        final AtomicInteger invocations = new AtomicInteger();
        final var terminal = new JPanel();
        final var dispatcher =
                new TerminalShortcutDispatcher(inputMap, actionMap, terminal::equals);
        final KeyStroke[] keyStrokes = {
            KeyStroke.getKeyStroke(KeyEvent.VK_1, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_2, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_3, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_4, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_5, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_6, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_7, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_8, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_9, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_K, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_W, SHORTCUT_MODIFIERS),
            KeyStroke.getKeyStroke(KeyEvent.VK_R, SHORTCUT_MODIFIERS | InputEvent.SHIFT_DOWN_MASK)
        };

        for (final KeyStroke keyStroke : keyStrokes) {
            inputMap.put(keyStroke, keyStroke);
            actionMap.put(keyStroke, action(invocations));

            final var event =
                    keyPressed(terminal, keyStroke.getKeyCode(), keyStroke.getModifiers());

            assertTrue(dispatcher.dispatchKeyEvent(event), "mapped shortcut should be handled");
            assertTrue(event.isConsumed(), "mapped shortcut should be consumed");
        }

        assertEquals(keyStrokes.length, invocations.get(), "every mapped shortcut should run");
    }

    @Test
    void leavesEscapeAndStandardTerminalKeysAvailable() {
        final InputMap inputMap = new InputMap();
        final ActionMap actionMap = new ActionMap();
        final var terminal = new JPanel();
        final var dispatcher =
                new TerminalShortcutDispatcher(inputMap, actionMap, terminal::equals);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
        actionMap.put("escape", action(new AtomicInteger()));
        final int[] standardKeys = {
            KeyEvent.VK_ESCAPE,
            KeyEvent.VK_A,
            KeyEvent.VK_ENTER,
            KeyEvent.VK_TAB,
            KeyEvent.VK_UP,
            KeyEvent.VK_LEFT
        };

        for (final int keyCode : standardKeys) {
            final var event = keyPressed(terminal, keyCode, 0);

            assertFalse(dispatcher.dispatchKeyEvent(event), "standard key should pass through");
            assertFalse(event.isConsumed(), "standard key should not be consumed");
        }
    }

    @Test
    void ignoresShortcutsOutsideTheTerminalAndNonPressedEvents() {
        final InputMap inputMap = new InputMap();
        final ActionMap actionMap = new ActionMap();
        final AtomicInteger invocations = new AtomicInteger();
        final var terminal = new JPanel();
        final var otherComponent = new JPanel();
        final var dispatcher =
                new TerminalShortcutDispatcher(inputMap, actionMap, terminal::equals);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_K, SHORTCUT_MODIFIERS), "command-palette");
        actionMap.put("command-palette", action(invocations));

        final var outsideEvent = keyPressed(otherComponent, KeyEvent.VK_K, SHORTCUT_MODIFIERS);
        final var releasedEvent =
                new KeyEvent(
                        terminal,
                        KeyEvent.KEY_RELEASED,
                        System.currentTimeMillis(),
                        SHORTCUT_MODIFIERS,
                        KeyEvent.VK_K,
                        'K');

        assertFalse(
                dispatcher.dispatchKeyEvent(outsideEvent),
                "shortcuts outside the terminal should pass through");
        assertFalse(
                dispatcher.dispatchKeyEvent(releasedEvent), "released keys should pass through");
        assertEquals(0, invocations.get(), "ignored events should not invoke actions");
    }

    private static AbstractAction action(final AtomicInteger invocations) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent event) {
                invocations.incrementAndGet();
            }
        };
    }

    private static KeyEvent keyPressed(
            final JComponent source, final int keyCode, final int modifiers) {
        return new KeyEvent(
                source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode, '\0');
    }
}
