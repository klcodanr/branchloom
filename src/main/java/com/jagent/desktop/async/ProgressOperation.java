package com.jagent.desktop.async;

import com.jagent.desktop.ui.components.LoadingPanel;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JDialog;

/**
 * UI helper that wraps a background task with transient progress UI state.
 *
 * <p>Use this class for user-initiated operations where the app should show a loading indicator and
 * wait cursor while work runs off the EDT. For background work that does not need UI progress, use
 * {@link BackgroundOperations} directly.
 */
public final class ProgressOperation {
    private final ProgressDialog progress;

    private ProgressOperation(final String title, final String message) {
        this.progress =
                GraphicsEnvironment.isHeadless() ? null : new ProgressDialog(title, message);
        if (this.progress != null) {
            this.progress.setVisible(true);
        }
    }

    private static final class ProgressDialog extends JDialog {
        public ProgressDialog(final String title, final String message) {
            super((Frame) null, title, ModalityType.MODELESS);
            setUndecorated(true);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            add(new LoadingPanel(message));
            setResizable(false);
            pack();
            setLocationRelativeTo(null);
        }
    }

    public void close() {
        if (progress != null) {
            progress.dispose();
        }
    }

    /**
     * Runs a background operation with a visible progress indicator and wait cursor.
     *
     * <p>The operation runs via {@link BackgroundOperations} and the returned future completes on
     * the EDT. The dialog and cursor are always cleaned up, even when the operation fails.
     *
     * @param <T> result type produced by the background operation
     * @param title progress dialog title and background task name
     * @param message progress message shown in the loading panel
     * @param operation blocking operation to run off the EDT
     * @return future completed on the EDT with the operation result or failure
     */
    public static <T> CompletableFuture<T> run(
            final String title, final String message, final Callable<T> operation) {
        final ProgressOperation progress = new ProgressOperation(title, message);
        return BackgroundOperations.submit(
                "Operations",
                title,
                () -> {
                    try {
                        return operation.call();
                    } finally {
                        progress.close();
                    }
                });
    }

    /**
     * Runs a background operation and keeps the progress UI visible until a follow-up operation
     * completes.
     *
     * @param <T> result type produced by the background operation
     * @param title progress dialog title and background task name
     * @param message progress message shown in the loading panel
     * @param operation blocking operation to run off the EDT
     * @param completion follow-up operation whose completion closes the progress UI
     * @return future completed when the follow-up operation finishes
     */
    public static <T> CompletableFuture<Void> runWithCompletion(
            final String title,
            final String message,
            final Callable<T> operation,
            final Function<T, CompletableFuture<?>> completion) {
        final ProgressOperation progress = new ProgressOperation(title, message);
        return BackgroundOperations.submit("Operations", title, operation)
                .thenCompose(value -> completion.apply(value).thenApply(ignored -> (Void) null))
                .whenComplete((ignored, failure) -> progress.close());
    }
}
