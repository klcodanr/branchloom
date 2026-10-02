package com.jagent.desktop.async;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.components.LoadingPanel;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import javax.swing.JDialog;

/**
 * UI helper that wraps a background task with transient progress UI state.
 *
 * <p>Use this class for user-initiated operations where the app should show a loading indicator and
 * wait cursor while work runs off the EDT. For background work that does not need UI progress, use
 * {@link BackgroundOperations} directly.
 */
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
            add(new LoadingPanel(message));
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
     * Runs a background operation with a visible progress indicator and wait cursor.
     *
     * <p>The operation runs via {@link BackgroundOperations} and the returned future completes on
     * the EDT. The dialog and cursor are always cleaned up, even when the operation fails.
     *
     * @param <T> result type produced by the background operation
     * @param actionContext source context that provides the parent window
     * @param title progress dialog title and background task name
     * @param message progress message shown in the loading panel
     * @param operation blocking operation to run off the EDT
     * @return future completed on the EDT with the operation result or failure
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
