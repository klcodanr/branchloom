package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportProjectDialogUiTest {
    private static final String REMOTE = "origin";

    @Test
    void validatesImportDetails(@TempDir final Path tempDirectory) throws IOException {
        final Path destination = Files.createDirectory(tempDirectory.resolve("project"));
        final Path file = Files.createFile(tempDirectory.resolve("file"));
        final Path missingParent = tempDirectory.resolve("missing").resolve("project");
        final Path populated = Files.createDirectory(tempDirectory.resolve("populated"));
        Files.createFile(populated.resolve("README.md"));

        assertEquals(
                "Enter a Git remote URL.",
                ImportProjectDialog.validationFailure(" ", destination.toString()),
                "remote URL should be required");
        assertEquals(
                "Choose a destination directory.",
                ImportProjectDialog.validationFailure(REMOTE, " "),
                "destination should be required");
        assertEquals(
                "The destination must be a directory.",
                ImportProjectDialog.validationFailure(REMOTE, file.toString()),
                "destination should be a directory");
        assertEquals(
                "The destination's parent directory must already exist.",
                ImportProjectDialog.validationFailure(REMOTE, missingParent.toString()),
                "destination parent should exist");
        assertEquals(
                "The destination directory must be empty.",
                ImportProjectDialog.validationFailure(REMOTE, populated.toString()),
                "destination should be empty");
        assertEquals(
                null,
                ImportProjectDialog.validationFailure(REMOTE, destination.toString()),
                "empty destination should be accepted");
    }

    @Test
    void enteringImportDetailsAndAcceptingReportsRequest(@TempDir final Path tempDirectory)
            throws IOException {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final Path destination = Files.createDirectory(tempDirectory.resolve("project"));
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final AtomicReference<ImportProjectDialog.Request> request = new AtomicReference<>();
        final Robot robot = BasicRobot.robotWithNewAwtHierarchy();
        final JFrame owner = GuiActionRunner.execute(() -> new JFrame());
        GuiActionRunner.execute(() -> owner.setVisible(true));
        final ImportProjectDialog dialog =
                GuiActionRunner.execute(
                        () ->
                                new ImportProjectDialog(
                                        new ActionContext(new ViewCoordinator(state), state, owner),
                                        request::set));
        final DialogFixture fixture = new DialogFixture(robot, dialog);

        try {
            fixture.show();
            fixture.textBox("import-remote").enterText(" git@github.com:example/project.git ");
            fixture.textBox("import-destination").enterText(destination.toString());
            GuiActionRunner.execute(() -> dialog.getRootPane().getDefaultButton().doClick());

            assertNotNull(request.get(), "accepting the dialog should submit a request");
            assertEquals(
                    "git@github.com:example/project.git",
                    request.get().remote(),
                    "remote should be trimmed before submission");
            assertEquals(
                    destination,
                    request.get().destination(),
                    "destination should be normalized before submission");
        } finally {
            fixture.cleanUp();
            owner.dispose();
            robot.cleanUp();
        }
    }
}
