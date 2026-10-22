package com.jagent.desktop.ui;

import com.jagent.desktop.services.JsonLogging;
import com.jagent.desktop.ui.utils.ErrorDialogs;
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
                        ErrorDialogs.showAndWait(
                                null, "Settings could not be loaded", exception.getMessage());
                        System.exit(1);
                    }
                });
    }
}
