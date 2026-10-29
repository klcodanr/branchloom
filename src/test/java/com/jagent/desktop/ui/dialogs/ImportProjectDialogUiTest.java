package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.test.TestAppState;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JPanelFixture;
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
                validationFailure("", destination),
                "remote URL should be required");
        assertEquals(
                "Choose a destination directory.",
                validationFailure(REMOTE, null),
                "destination should be required");
        assertEquals(
                "The destination must be a directory.",
                validationFailure(REMOTE, file),
                "parent folder should be a directory");
        assertEquals(
                "The destination's parent directory must already exist.",
                validationFailure(REMOTE, missingParent),
                "parent folder should exist");
        assertEquals(
                "The destination directory must be empty.",
                validationFailure(REMOTE, populated),
                "destination should be empty");
        assertEquals(
                null,
                validationFailure(REMOTE, destination),
                "empty destination should be accepted");
    }

    private static String validationFailure(final String remote, final Path destination) {
        try {
            final var method =
                    ImportProjectDialog.class.getDeclaredMethod(
                            "validationFailure", String.class, String.class);
            method.trySetAccessible();
            return (String)
                    method.invoke(null, remote, destination == null ? "" : destination.toString());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not validate import details", exception);
        }
    }

    @Test
    void browserStartsAtEnteredDestinationOrHomeWhenBlank(@TempDir final Path tempDirectory) {
        assertEquals(
                tempDirectory.toString(),
                ImportProjectDialog.initialDirectory("  " + tempDirectory + "  "),
                "browser should use the trimmed destination");
        assertEquals(
                System.getProperty("user.home"),
                ImportProjectDialog.initialDirectory(" \t "),
                "browser should use home when destination is blank");
    }

    @Test
    void enteringImportDetailsAndAcceptingReportsRequest(@TempDir final Path tempDirectory)
            throws IOException {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final Path destination = Files.createDirectory(tempDirectory.resolve("project"));
        final AtomicReference<ImportProjectDialog.Request> request = new AtomicReference<>();
        final Robot robot = BasicRobot.robotWithNewAwtHierarchy();
        final JFrame owner = GuiActionRunner.execute(() -> new JFrame());
        GuiActionRunner.execute(() -> owner.setVisible(true));
        final ImportProjectDialog dialog =
                GuiActionRunner.execute(
                        () ->
                                new ImportProjectDialog(
                                        TestAppState.dialogService(), List.of(), request::set));
        final JPanelFixture fixture = new JPanelFixture(robot, dialog);

        try {
            GuiActionRunner.execute(
                    () -> {
                        owner.setContentPane(dialog);
                        owner.pack();
                        owner.setVisible(true);
                    });
            fixture.textBox("import-remote").enterText(" git@github.com:example/project.git ");
            fixture.textBox("import-destination").enterText(destination.toString());
            GuiActionRunner.execute(() -> fixture.button("import-ok").target().doClick());

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
            owner.dispose();
            robot.cleanUp();
        }
    }
}
