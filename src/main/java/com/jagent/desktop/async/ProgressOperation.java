package com.jagent.desktop.async;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import javax.swing.JDialog;

/** Runs a slow operation off the EDT while displaying progress and reporting failures. */
public final class ProgressOperation {
    private final Window window;
    private final ProgressDialog progress;

    private ProgressOperation(
            final ActionContext actionContext, final String title, final String message) {
        this.window = actionContext == null ? null : actionContext.window();
        if (this.window != null) {
            this.window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        this.progress =
                GraphicsEnvironment.isHeadless()
                        ? null
                        : new ProgressDialog(this.window, title, message);
        if (this.progress != null) {
            this.progress.setVisible(true);
        }
    }

    private static final class ProgressDialog extends JDialog {
        public ProgressDialog(final Window owner, final String title, final String message) {
            super(owner, title, ModalityType.MODELESS);
            setUndecorated(true);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            add(UiFactory.loading(message));
            setResizable(false);
            pack();
            setLocationRelativeTo(owner);
        }
    }

    public void close() {
        if (progress != null) {
            progress.dispose();
        }
        if (window != null) {
            window.setCursor(Cursor.getDefaultCursor());
        }
    }

    /**
     * Runs a background operation while displaying progress and reporting failures.
     *
     * <p>This method returns a CompletableFuture that will be completed with the result of the
     * operation once it finishes, or completed exceptionally if the operation fails.
     *
     * <p>The progress dialog will be displayed while the operation is running and closed once it
     * completes. Note that this method does not block the calling thread; it returns immediately
     * with a CompletableFuture.
     *
     * @param <T> the type of the result produced by the background operation
     * @param actionContext the context of the action, providing access to the parent window
     * @param title the title of the progress dialog
     * @param message the message to display in the progress dialog
     * @param operation the background operation to execute
     * @return a CompletableFuture that will be completed with the result of the operation or
     *     exceptionally if it fails
     */
    public static <T> CompletableFuture<T> run(
            final ActionContext actionContext,
            final String title,
            final String message,
            final Callable<T> operation) {
        final ProgressOperation progress = new ProgressOperation(actionContext, title, message);
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
}
