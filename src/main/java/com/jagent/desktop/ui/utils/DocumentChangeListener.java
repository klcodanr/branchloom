package com.jagent.desktop.ui.utils;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class DocumentChangeListener implements DocumentListener {
    private final Runnable listener;

    public DocumentChangeListener(final Runnable listener) {
        this.listener = listener;
    }

    @Override
    public void insertUpdate(final DocumentEvent event) {
        listener.run();
    }

    @Override
    public void removeUpdate(final DocumentEvent event) {
        listener.run();
    }

    @Override
    public void changedUpdate(final DocumentEvent event) {
        listener.run();
    }
}
