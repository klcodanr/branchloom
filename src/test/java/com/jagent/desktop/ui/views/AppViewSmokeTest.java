package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        final AppView view = GuiActionRunner.execute(() -> new AppView(directory));

        assertNotNull(view.getJMenuBar(), "application should configure a menu bar");
        assertEquals("Branchloom", view.getTitle(), "window title should match");
        assertTrue(view.getComponentCount() > 0, "window should render content");

        GuiActionRunner.execute(
                () -> {
                    view.dispose();
                    return null;
                });
    }
}
