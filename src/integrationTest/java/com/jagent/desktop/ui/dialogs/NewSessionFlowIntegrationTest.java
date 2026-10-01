package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewSessionFlowIntegrationTest {
    @Test
    void submitsSessionDetailsAndCreatesItsGitWorktree(@TempDir final Path directory)
            throws IOException, InterruptedException {
        initializeRepository(directory);
        final Agent agent = new Agent("Test agent", "true {prompt}");
        final AppState state = stateWithAgent(agent);
        final Project project = new Project("Demo", directory.toString(), null);
        final var projectId = state.addProject(project);
        state.updateCurrentProject(projectId);
        final AtomicReference<NewSessionDialog.Request> request = new AtomicReference<>();
        final Robot robot = BasicRobot.robotWithNewAwtHierarchy();
        final JFrame owner = GuiActionRunner.execute(() -> new JFrame());
        final NewSessionDialog dialog =
                GuiActionRunner.execute(
                        () ->
                                new NewSessionDialog(
                                        new ActionContext(new ViewCoordinator(state), state, owner),
                                        request::set));
        final DialogFixture fixture = new DialogFixture(robot, dialog);

        try {
            GuiActionRunner.execute(() -> owner.setVisible(true));
            fixture.show();
            GuiActionRunner.execute(
                    () -> {
                        fixture.textBox("session-name").target().setText(" Demo session ");
                        fixture.textBox("session-prompt").target().setText("Run the tests");
                    });
            GuiActionRunner.execute(() -> dialog.getRootPane().getDefaultButton().doClick());

            assertNotNull(request.get(), "valid session details should be submitted");
            assertEquals("Demo session", request.get().name(), "session name should be trimmed");
            assertEquals("Run the tests", request.get().prompt(), "prompt should be submitted");

            final Path worktree = directory.resolveSibling(directory.getFileName() + "-session");
            run(
                    directory,
                    "git worktree add -q -b demo-session "
                            + PlatformCommands.shellQuote(worktree.toString())
                            + " master");

            assertTrue(Files.isDirectory(worktree), "successful session should have a worktree");
            assertEquals("demo-session\n", branch(worktree), "worktree branch should match");
            assertEquals(projectId, state.currentProjectId(), "project should remain available");
        } finally {
            fixture.cleanUp();
            owner.dispose();
            robot.cleanUp();
        }
    }

    @Test
    void cancellingSessionDialogDoesNotSubmitARequest(@TempDir final Path directory) {
        final Agent agent = new Agent("Test agent", "true {prompt}");
        final AppState state = stateWithAgent(agent);
        final AtomicReference<NewSessionDialog.Request> request = new AtomicReference<>();
        final Robot robot = BasicRobot.robotWithNewAwtHierarchy();
        final JFrame owner = GuiActionRunner.execute(() -> new JFrame());
        final NewSessionDialog dialog =
                GuiActionRunner.execute(
                        () ->
                                new NewSessionDialog(
                                        new ActionContext(new ViewCoordinator(state), state, owner),
                                        request::set));
        final DialogFixture fixture = new DialogFixture(robot, dialog);

        try {
            GuiActionRunner.execute(() -> owner.setVisible(true));
            fixture.show();
            final var cancel = fixture.button("session-cancel").target();
            GuiActionRunner.execute((GuiActionRunnable) cancel::doClick);

            assertFalse(dialog.isVisible(), "cancelling should close the dialog");
            assertTrue(request.get() == null, "cancelling should not submit a request");
        } finally {
            fixture.cleanUp();
            owner.dispose();
            robot.cleanUp();
        }
    }

    private static AppState stateWithAgent(final Agent agent) {
        final AppSettings defaults = Defaults.appSettings();
        return new AppState(
                new AppSettings(
                        List.of(agent),
                        defaults.groupOrder(),
                        defaults.reviewPrompt(),
                        defaults.theme(),
                        defaults.tools(),
                        defaults.worktreeTemplate(),
                        defaults.agentContextPath()),
                Map.of(),
                Map.of(),
                Map.of());
    }

    private static void initializeRepository(final Path directory)
            throws IOException, InterruptedException {
        run(directory, "git init -q -b master");
        Files.writeString(directory.resolve("tracked.txt"), "content");
        run(
                directory,
                "git add tracked.txt && git -c user.name=test -c user.email=test commit -qm initial");
    }

    private static String branch(final Path directory) throws IOException, InterruptedException {
        return command(directory, "git branch --show-current");
    }

    private static void run(final Path directory, final String command)
            throws IOException, InterruptedException {
        assertEquals(
                0,
                new ProcessBuilder(PlatformCommands.shell(command))
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(),
                "repository setup should succeed");
    }

    private static String command(final Path directory, final String command)
            throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder(PlatformCommands.shell(command))
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .start();
        return new String(
                process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
