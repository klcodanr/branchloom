package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.services.BackgroundTasks;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Runs a slow operation off the EDT while displaying progress and reporting failures. */
public final class ProgressOperation {
    private final ProgressDialog progress;

    private ProgressOperation(final Window owner, final String title, final String message) {
        this.progress =
                GraphicsEnvironment.isHeadless() ? null : new ProgressDialog(owner, title, message);
        if (this.progress != null) {
            this.progress.setVisible(true);
        }
    }

    public static ProgressOperation start(
            final Window owner, final String title, final String message) {
        return new ProgressOperation(owner, title, message);
    }

    public void close() {
        if (progress != null) {
            progress.dispose();
        }
    }

    public static void run(
            final Window owner,
            final String title,
            final String message,
            final Callable<?> operation,
            final Runnable onSuccess,
            final Consumer<Throwable> onFailure) {
        final ProgressOperation progress = start(owner, title, message);
        BackgroundTasks.submit(
                "Operations",
                title,
                () -> {
                    try {
                        operation.call();
                        SwingUtilities.invokeLater(
                                () -> {
                                    progress.close();
                                    onSuccess.run();
                                });
                    } catch (Throwable failure) {
                        SwingUtilities.invokeLater(
                                () -> {
                                    progress.close();
                                    onFailure.accept(failure);
                                });
                    }
                });
    }
}
