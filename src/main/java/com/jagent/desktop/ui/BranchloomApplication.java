package com.jagent.desktop.ui;

import com.formdev.flatlaf.util.SystemInfo;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PullRequestWatcher;
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
    private final Path dataDirectory;
    private final AppStatePersistence persistence;
    private final WindowStatePersistence windowStatePersistence;
    private final PullRequestWatcher pullRequestWatcher;
    private final AppState state;
    private final ViewController controller;
    private final ApplicationWindow window;
    private final AppView view;
    private final ActionContext actionContext;
    private boolean closed;

    public BranchloomApplication(final Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        state = AppStatePersistence.load(dataDirectory);
        persistence = new AppStatePersistence(state, dataDirectory);

        windowStatePersistence = new WindowStatePersistence(dataDirectory);
        window = new ApplicationWindow(windowStatePersistence, this::close);

        final Runnable refreshSettings =
                () -> {
                    final var settings = persistence.reloadSettings();
                    Theme.apply(Theme.FlatLafTheme.from(settings.theme()));
                };

        controller = new ViewController(state, window, dataDirectory, refreshSettings);
        actionContext =
                new ActionContext(
                        controller,
                        state,
                        window,
                        dataDirectory,
                        () -> {
                            refreshSettings.run();
                            controller.updateView(controller.currentViewId(), null);
                        });
        view = new AppView(actionContext);
        window.setJMenuBar(AppMenuBar.create(actionContext));
        window.setContentPane(view);

        controller.onViewChanged(
                ignored -> {
                    window.setJMenuBar(AppMenuBar.create(actionContext));
                    view.render();
                });
        pullRequestWatcher = new PullRequestWatcher(actionContext);
    }

    public void start() {
        configurePlatform();
        configureSwing();
        ClipboardImagePaster.cleanupStaleImages();
        if (Taskbar.isTaskbarSupported()) {
            Taskbar.getTaskbar().setIconImage(AppIcon.image(128));
        }
        pullRequestWatcher.start();
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
        pullRequestWatcher.close();
        ProgressOperation.run(
                        new ActionContext(controller, state, window, dataDirectory, null),
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
