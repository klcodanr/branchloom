package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectImportAndCreateActionTest {
    private static final String PROJECT_NAME = "Demo";

    @Test
    void createAndImportActionsExposeExpectedMetadata(@TempDir final Path tempDir) {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);
        final CreateProjectAction createAction = new CreateProjectAction(context);
        final ImportProjectAction importAction = new ImportProjectAction(context);

        assertEquals("new-project", createAction.id(), "create action id should be stable");
        assertEquals("Add local project", createAction.label(), "create action label should match");
        assertEquals("import-project", importAction.id(), "import action id should be stable");
        assertEquals(
                "Clone remote project", importAction.label(), "import action label should match");
    }

    @Test
    void contextContainsExistingProjectThatValidationWouldReject(@TempDir final Path tempDir) {
        final Path projectPath = tempDir.resolve("demo-project");
        final Project project = new Project(PROJECT_NAME, projectPath.toString(), null);
        final ProjectId projectId = ProjectId.create();
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());

        assertFalse(state.projects().isEmpty(), "test state should include existing projects");
    }
}
