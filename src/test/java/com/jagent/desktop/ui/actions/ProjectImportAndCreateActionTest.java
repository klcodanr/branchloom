package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final String PROJECT_NAME_CASE = "demo";

    @Test
    void createProjectHelperMethodsHandleDuplicatesAndInitialDirectory(
            @TempDir final Path tempDir) {
        final Path projectPath = tempDir.resolve("demo-project");
        final Project project = new Project(PROJECT_NAME, projectPath.toString(), null);
        assertTrue(
                CreateProjectAction.duplicateName(java.util.List.of(project), PROJECT_NAME_CASE),
                "duplicateName should ignore letter case");
        assertTrue(
                CreateProjectAction.duplicatePath(
                        java.util.List.of(project), projectPath.toAbsolutePath()),
                "duplicatePath should match normalized absolute path");
        assertEquals(
                System.getProperty("user.home"),
                CreateProjectAction.initialDirectory(" "),
                "blank initial directory should use user home");
    }

    @Test
    void importProjectValidationAndFailureMessagesHandleEdgeCases(@TempDir final Path tempDir) {
        final ProjectId projectId = ProjectId.create();
        final Path existingPath = tempDir.resolve("existing-project");
        final Project existing = new Project(PROJECT_NAME, existingPath.toString(), null);
        final Path otherPath = tempDir.resolve("other-project");
        final AppState state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), existing),
                        Map.of(),
                        Map.of());
        final ActionContext context = new ActionContext(new ViewCoordinator(state), state, null);
        final ImportProjectAction action = new ImportProjectAction(context);

        assertEquals(
                "A project with that name is already registered.",
                action.registrationFailure(PROJECT_NAME_CASE, otherPath),
                "existing project names should be rejected");
        assertEquals(
                "That destination is already registered as a project.",
                action.registrationFailure("Another", existingPath.toAbsolutePath()),
                "existing project paths should be rejected");
        assertNull(
                action.registrationFailure("Another", otherPath),
                "unique name/path should pass validation");

        assertEquals(
                "Git did not provide more details.",
                ImportProjectAction.message(new RuntimeException("  ")),
                "blank failures should use fallback message");
    }
}
