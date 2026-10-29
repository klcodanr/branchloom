package com.jagent.desktop.ui;

import com.formdev.flatlaf.util.SystemInfo;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ApplicationModule;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.persistence.AppStatePersistence;
import com.jagent.desktop.services.persistence.WindowStatePersistence;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.ui.components.AppIcon;
import com.jagent.desktop.ui.components.AppMenuBar;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.utils.ClipboardImagePaster;
import com.jagent.desktop.ui.views.AppView;
import java.awt.Taskbar;
import java.nio.file.Path;
import javax.swing.UIManager;

/** Explicit composition root and lifecycle owner for the desktop application. */
public final class BranchloomApplication {
    private final AppStatePersistence persistence;
    private final WindowStatePersistence windowStatePersistence;
    private final AppState state;
    private final ApplicationWindow window;
    private final AppView view;
    private final AppMenuBar menuBar;
    private boolean closed;

    public BranchloomApplication(final Path dataDirectory) {
        state = AppStatePersistence.load(dataDirectory);
        Theme.apply(Theme.FlatLafTheme.from(state.appSettings().theme()));
        persistence = new AppStatePersistence(state, dataDirectory);
        windowStatePersistence = new WindowStatePersistence(dataDirectory);
        window = new ApplicationWindow(windowStatePersistence, this::close);

        final Runnable refreshSettings =
                () -> {
                    final var settings = persistence.reloadSettings();
                    Theme.apply(Theme.FlatLafTheme.from(settings.theme()));
                };

        final Injector injector =
                Guice.createInjector(
                        new ApplicationModule(dataDirectory, state, window, refreshSettings));
        view = injector.getInstance(AppView.class);
        menuBar = injector.getInstance(AppMenuBar.class);
        final var controller = injector.getInstance(ViewController.class);
        window.setJMenuBar(menuBar.create());
        window.setContentPane(view);

        controller.onViewChanged(
                ignored -> {
                    menuBar.create();
                    window.revalidate();
                    window.repaint();
                    view.render();
                });
    }

    public void start() {
        configurePlatform();
        configureSwing();
        ClipboardImagePaster.cleanupStaleImages();
        if (Taskbar.isTaskbarSupported()) {
            Taskbar.getTaskbar().setIconImage(AppIcon.image(128));
        }
        window.setIconImage(AppIcon.image(64));
        window.setVisible(true);
    }

    private static void configureSwing() {
        UIManager.put("OptionPane.questionIcon", null);
        UIManager.put("OptionPane.informationIcon", null);
        UIManager.put("OptionPane.warningIcon", null);
        UIManager.put("OptionPane.errorIcon", null);
    }

    private static void configurePlatform() {
        if (!SystemInfo.isMacOS) {
            return;
        }
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Branchloom");
        System.setProperty("apple.awt.application.appearance", "system");
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        ProgressOperation.run(
                        "Closing Branchloom",
                        "Stopping terminal processes...",
                        () -> {
                            TerminalManager.get().disposeAll();
                            return null;
                        })
                .thenRun(
                        () -> {
                            view.disposeContent();
                            window.dispose();
                            persistence.close();
                            windowStatePersistence.close();
                        });
    }
}
