package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class GlobalSettingsViewTest {
    @Test
    void rendersAndSavesSettings() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ViewCoordinator coordinator = new ViewCoordinator(state);
        final ActionContext context = new ActionContext(coordinator, state, null);
        final GlobalSettingsView view = new GlobalSettingsView(context);

        final JComponent rendered = GuiActionRunner.execute(view::render);
        final JButton save = SwingTestSupport.findButton(rendered, "Save");

        assertEquals(ViewId.SETTINGS, view.id(), "settings view should expose settings id");
        assertEquals("Settings", view.title(), "settings view should expose title");
        assertNotNull(view.render(), "render should produce a component");
        assertNotNull(save, "settings screen should expose save button");

        GuiActionRunner.execute(
                () -> {
                    save.doClick();
                    return null;
                });
        assertEquals(
                ViewId.HOME,
                coordinator.currentViewId(),
                "saving settings should return to home view");
    }
}
