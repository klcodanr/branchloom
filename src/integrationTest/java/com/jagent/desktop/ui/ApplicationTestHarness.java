package com.jagent.desktop.ui;

import com.jagent.desktop.ui.views.AppView;
import java.awt.Window;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Test-only composition fixture for exercising the application shell. */
public final class ApplicationTestHarness implements AutoCloseable {
    private final BranchloomApplication application;
    private final ApplicationWindow window;

    public ApplicationTestHarness(final Path dataDirectory) {
        final List<Window> existingWindows = windows();
        application = new BranchloomApplication(dataDirectory);
        window =
                windows().stream()
                        .filter(current -> !existingWindows.contains(current))
                        .filter(ApplicationWindow.class::isInstance)
                        .map(ApplicationWindow.class::cast)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Application window was not created"));
    }

    public void start() {
        application.start();
    }

    public AppView view() {
        final java.awt.Container content = window.getContentPane();
        if (content instanceof AppView appView) {
            return appView;
        }
        throw new IllegalStateException("Application view was not created");
    }

    public ApplicationWindow window() {
        return window;
    }

    private static List<Window> windows() {
        return Arrays.asList(Window.getWindows());
    }

    @Override
    public void close() {
        application.close();
    }
}
