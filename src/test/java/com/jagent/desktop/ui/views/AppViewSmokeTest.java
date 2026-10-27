package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.persistence.AppStatePersistence;
import com.jagent.desktop.services.persistence.WindowStatePersistence;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.ApplicationWindow;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppViewSmokeTest {
    @Test
    void createsAndDisposesWindow(@TempDir final Path directory) {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "requires graphics environment");

        final AppState state = AppStatePersistence.load(directory);
        try (AppStatePersistence ignored = new AppStatePersistence(state, directory);
                WindowStatePersistence windowPersistence = new WindowStatePersistence(directory)) {
            final ApplicationWindow window =
                    GuiActionRunner.execute(
                            () -> new ApplicationWindow(windowPersistence, () -> {}));
            final AppView view =
                    GuiActionRunner.execute(
                            () ->
                                    new AppView(
                                            new ActionContext(
                                                    TestAppState.controller(state),
                                                    state,
                                                    window,
                                                    directory,
                                                    () -> {})));
            GuiActionRunner.execute(
                    () -> {
                        window.setContentPane(view);
                        window.setVisible(true);
                    });

            assertEquals("Branchloom", window.getTitle(), "window title should match");
            assertTrue(view.getComponentCount() > 0, "window should render content");

            GuiActionRunner.execute(
                    () -> {
                        view.disposeContent();
                        window.dispose();
                    });
        }
    }
}
