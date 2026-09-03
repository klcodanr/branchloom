package com.jagent.desktop.ui.utils;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Predicate;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;

public final class TerminalShortcutDispatcher implements KeyEventDispatcher {
    private final InputMap inputMap;
    private final ActionMap actionMap;
    private final Predicate<Component> isTerminalComponent;

    public TerminalShortcutDispatcher(
            final InputMap inputMap,
            final ActionMap actionMap,
            final Predicate<Component> isTerminalComponent) {
        this.inputMap = inputMap;
        this.actionMap = actionMap;
        this.isTerminalComponent = isTerminalComponent;
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED
                || event.getKeyCode() == KeyEvent.VK_ESCAPE
                || !(event.getSource() instanceof Component component)
                || !isTerminalComponent.test(component)) {
            return false;
        }
        final KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(event);
        final Object actionId = inputMap.get(keyStroke);
        if (actionId == null) {
            return false;
        }
        final Action action = actionMap.get(actionId);
        if (action == null) {
            return false;
        }
        action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
        event.consume();
        return true;
    }
}
