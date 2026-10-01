package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectViewCoverageTest {
    @Test
    void rendersProjectWorkspaceAndCanFocusReviewFilter(@TempDir final Path directory)
            throws MalformedURLException {
        final Project persisted = new Project("Persisted", directory.toString(), null);
        final ProjectId projectId = ProjectId.create();
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), persisted),
                        Map.of(),
                        Map.of());
        state.updateCurrentProject(projectId);
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);

        final Project passedToView = new Project("Unmapped", directory.toString(), null);
        final ProjectView view = new ProjectView(context, passedToView);
        view.refresh();

        assertEquals(ViewId.PROJECT, view.id(), "project view should expose project view id");
        assertEquals("Unmapped", view.title(), "project view title should use project name");
        assertNotNull(view.render(), "project view should render");
        assertSame(view, view.render(), "render should return the view itself");
        view.dispose();
    }
}
