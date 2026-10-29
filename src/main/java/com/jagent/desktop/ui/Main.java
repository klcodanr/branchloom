package com.jagent.desktop.ui;

import com.jagent.desktop.services.JsonLogging;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.dialog.SwingDialogService;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/** Application entry point. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        try {
            JsonLogging.configure();
        } catch (Exception ignored) {
            // Logging setup should not prevent the application from opening.
        }
        SwingUtilities.invokeLater(
                () -> {
                    try {
                        new BranchloomApplication(
                                        Path.of(System.getProperty("user.home"), ".branchloom"))
                                .start();
                    } catch (IllegalStateException exception) {
                        new SwingDialogService(null)
                                .message(
                                        exception.getMessage(),
                                        "Settings could not be loaded",
                                        DialogService.MessageType.ERROR);
                        System.exit(1);
                    }
                });
    }
}
