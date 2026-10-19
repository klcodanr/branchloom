package com.jagent.desktop.ui.components;

import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Set;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

/** Text area with form-friendly Tab traversal. */
@SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
public class BaseTextArea extends JTextArea {
    public BaseTextArea() {
        super();
        this.setLineWrap(true);
        this.setWrapStyleWord(true);
        configureTraversal();
    }

    public BaseTextArea(final String text) {
        super(text);
        this.setLineWrap(true);
        this.setWrapStyleWord(true);
        configureTraversal();
    }

    public BaseTextArea(final int rows, final int columns) {
        super(rows, columns);
        this.setLineWrap(true);
        this.setWrapStyleWord(true);
        configureTraversal();
    }

    public BaseTextArea(final String text, final int rows, final int columns) {
        super(text, rows, columns);
        this.setLineWrap(true);
        this.setWrapStyleWord(true);
        configureTraversal();
    }

    private void configureTraversal() {
        setFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                Set.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)));
        setFocusTraversalKeys(
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                Set.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)));
    }
}
