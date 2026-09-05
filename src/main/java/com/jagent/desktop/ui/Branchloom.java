package com.jagent.desktop.ui;

import com.formdev.flatlaf.util.SystemInfo;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.JsonLogging;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.ui.components.AppIcon;
import com.jagent.desktop.ui.utils.ClipboardImagePaster;
import com.jagent.desktop.ui.views.AppView;
import java.awt.Taskbar;
import javax.swing.SwingUtilities;

/** Application bootstrap and platform-level setup. */
public final class Branchloom {
    private Branchloom() {}

    public static void main(String[] args) {
        try {
            JsonLogging.configure();
        } catch (Exception ignored) {
            // Logging setup should not prevent the application from opening.
        }
        ClipboardImagePaster.cleanupStaleImages();
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    TerminalManager.get().disposeAll();
                                    BackgroundTasks.shutdown();
                                }));
        configureMacOs();
        if (Taskbar.isTaskbarSupported()) {
            Taskbar.getTaskbar().setIconImage(AppIcon.image(128));
        }
        SwingUtilities.invokeLater(() -> new AppView().setVisible(true));
    }

    private static void configureMacOs() {
        if (!SystemInfo.isMacOS) {
            return;
        }
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Branchloom");
        System.setProperty("apple.awt.application.appearance", "system");
    }
}
