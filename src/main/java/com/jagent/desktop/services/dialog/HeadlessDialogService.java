package com.jagent.desktop.services.dialog;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.swing.JComponent;

/** Deterministic dialog implementation for headless tests and non-interactive runs. */
public final class HeadlessDialogService implements DialogService {
    private final Queue<Object> results = new ArrayDeque<>();

    public HeadlessDialogService() {}

    public HeadlessDialogService(final Object... results) {
        for (final Object result : results) {
            this.results.add(result);
        }
    }

    public void enqueue(final Object result) {
        results.add(result);
    }

    @Override
    public void message(
            final String message,
            final String title,
            final DialogService.MessageType messageType) {}

    @Override
    public void errorMessage(String message, String title) {}

    @Override
    public DialogService.ReturnType confirm(
            final Object content,
            final String title,
            final DialogService.OptionType optionType,
            final DialogService.MessageType messageType) {
        return next(DialogService.ReturnType.CANCEL);
    }

    @Override
    public Object input(
            final Object message, final String title, final DialogService.MessageType messageType) {
        return next(null);
    }

    @Override
    public DialogService.ReturnType option(
            final Object message,
            final String title,
            final DialogService.MessageType messageType,
            final Object[] options,
            final Object initialValue) {
        return next(DialogService.ReturnType.CLOSED);
    }

    @Override
    public Path chooseDirectory(
            final String title, final Path initialDirectory, final String approveButtonText) {
        return next(null);
    }

    @Override
    public DialogService.ReturnType show(
            final String title, final JComponent content, final String primaryLabel) {
        return next(DialogService.ReturnType.CANCEL);
    }

    private <T> T next(final T fallback) {
        final Object result = results.poll();
        return result == null ? fallback : (T) result;
    }
}
