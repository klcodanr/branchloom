package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.components.tree.items.GroupTreeItem;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewProjectActionTest {
    private static final String PROJECT_NAME = "Demo";
    private static final String PROMPT = "Build a demo's UI.\nUse the supplied $theme.";
    private static final String CREATE_BUTTON = "new-project-create";

    @Test
    void createsEmptyRepositoryAndSelectsAgentTerminal(@TempDir final Path parent)
            throws InterruptedException, ExecutionException, TimeoutException {
        assertCreatesProject(null, parent);
    }

    @Test
    void createsProjectInGroupFromContextMenu(@TempDir final Path parent)
            throws InterruptedException, ExecutionException, TimeoutException {
        assertCreatesProject("Work", parent);
    }

    private static void assertCreatesProject(final String group, final Path parent)
            throws InterruptedException, ExecutionException, TimeoutException {
        final var fixture = new Fixture();
        final var view = new CompletableFuture<ViewId>();
        final Path destination = parent.resolve("demo repo");
        GuiActionRunner.execute(
                () -> {
                    fixture.controller.onViewChanged(view::complete);
                    if (group == null) {
                        fixture.open();
                    } else {
                        final var menu =
                                new GroupTreeItem(
                                                fixture.controller,
                                                fixture.state,
                                                fixture.dialogs(),
                                                group)
                                        .getMenu();
                        ((JMenuItem) menu.getComponent(0)).doClick(0);
                    }
                    component(fixture.form.get(), "Choose parent...", JButton.class).doClick(0);
                    fixture.parent.set(parent);
                    component(fixture.form.get(), "Choose parent...", JButton.class).doClick(0);
                    assertEquals(
                            parent + java.io.File.separator,
                            fixture.folderInput().getText(),
                            "browse should fill the parent");
                    fixture.fill(" Demo ", parent.resolve("unused/../demo repo").toString());
                    component(fixture.form.get(), "new-project-agent", JComboBox.class)
                            .setSelectedIndex(1);
                    fixture.click(CREATE_BUTTON);
                });

        assertEquals(ViewId.PROJECT, view.get(10, TimeUnit.SECONDS), "should open the new project");
        final var projectId = fixture.controller.currentProjectId();
        final var terminal = fixture.state.terminals().get(fixture.controller.currentTerminalId());
        assertTrue(Files.isRegularFile(destination.resolve(".git/HEAD")), "should initialize Git");
        assertEquals(
                PROJECT_NAME,
                fixture.state.projects().get(projectId).name(),
                "should trim project name");
        assertEquals(
                destination.toString(),
                fixture.state.projects().get(projectId).path(),
                "should store the normalized folder");
        assertEquals(
                Optional.ofNullable(group).orElse(Defaults.DEFAULT_GROUP),
                fixture.state.projects().get(projectId).group(),
                "should register the project in the target group");
        assertEquals(projectId, terminal.owner(), "terminal should belong to the project");
        assertEquals("Second agent", terminal.title(), "should use the selected agent");
        assertEquals(
                "second --prompt " + PlatformCommands.shellQuote(PROMPT),
                terminal.command(),
                "should pass the complete prompt as one argument");
        assertNull(fixture.controller.currentSessionId(), "should not create a worktree session");
        assertNull(
                fixture.form.get().getClientProperty("dialog.cancelled"),
                "successful submission should not be cancelled");
    }

    @Test
    void invalidInputAndCancellationDoNotCreateProject(@TempDir final Path parent) {
        final var fixture = new Fixture();
        final var destination = parent.resolve("cancelled");
        GuiActionRunner.execute(
                () -> {
                    fixture.open();
                    fixture.click(CREATE_BUTTON);
                    assertTrue(
                            fixture.error.getNow("").contains("Enter a project name"),
                            "empty form should show validation");
                    fixture.fill(PROJECT_NAME, destination.toString());
                    fixture.click("new-project-cancel");
                    assertEquals(
                            Boolean.TRUE,
                            fixture.form.get().getClientProperty("dialog.cancelled"),
                            "cancel should close the form without submitting");
                });
        assertFalse(Files.exists(destination), "cancel should not create a folder");
        assertTrue(fixture.state.projects().isEmpty(), "cancel should not register a project");
    }

    @Test
    void invalidPathKeepsFormOpen() {
        final var fixture = new Fixture();
        GuiActionRunner.execute(
                () -> {
                    fixture.open();
                    fixture.fill(PROJECT_NAME, "bad\0path");
                    fixture.click(CREATE_BUTTON);
                    assertNull(
                            fixture.form.get().getClientProperty("dialog.completed"),
                            "invalid path should keep the form open");
                });
        assertEquals(
                "Enter a valid path for the new folder.",
                fixture.error.getNow(""),
                "should explain the path error");
    }

    @Test
    void duplicateProjectNameDoesNotCreateDirectory(@TempDir final Path parent) {
        final var fixture = new Fixture();
        final var destination = parent.resolve("new");
        GuiActionRunner.execute(
                () -> {
                    fixture.state.addProject(
                            Project.create("DEMO", parent.resolve("old").toString(), null));
                    fixture.open();
                    fixture.fill(PROJECT_NAME, destination.toString());
                    fixture.click(CREATE_BUTTON);
                });
        assertTrue(
                fixture.error.getNow("").contains("already exists"),
                "should reject a duplicate name");
        assertFalse(
                Files.exists(destination),
                "duplicate validation should run before Git initialization");
    }

    @Test
    void existingFolderIsNotModifiedOrRegistered(@TempDir final Path destination)
            throws InterruptedException, ExecutionException, TimeoutException {
        final var fixture = new Fixture();
        GuiActionRunner.execute(
                () -> {
                    fixture.open();
                    fixture.fill(PROJECT_NAME, destination.toString());
                    fixture.click(CREATE_BUTTON);
                });
        assertTrue(
                fixture.error
                        .get(10, TimeUnit.SECONDS)
                        .contains("Could not create the project folder"),
                "should report folder creation failure");
        assertFalse(
                Files.exists(destination.resolve(".git")),
                "should not initialize an existing folder");
        assertTrue(fixture.state.projects().isEmpty(), "should not register a failed project");
        assertTrue(
                fixture.state.terminals().isEmpty(), "should not register a terminal on failure");
    }

    @Test
    void requiresAnAgentThatAcceptsPrompts() {
        final var fixture = new Fixture(List.of(new Agent("No prompt support", "agent")));
        GuiActionRunner.execute(fixture::open);
        assertTrue(
                fixture.error.getNow("").contains("Configure an agent"),
                "should explain the missing agent configuration");
        assertNull(fixture.form.get(), "should not show a form with no usable agent");
    }

    private static <T extends Component> T component(
            final Container root, final String name, final Class<T> type) {
        final var pending = new ArrayDeque<Component>();
        pending.add(root);
        while (!pending.isEmpty()) {
            final Component next = pending.remove();
            if (type.isInstance(next)
                    && (name.equals(next.getName())
                            || next instanceof JButton button && name.equals(button.getText()))) {
                return type.cast(next);
            }
            if (next instanceof Container container) {
                Collections.addAll(pending, container.getComponents());
            }
        }
        throw new AssertionError("Missing component: " + name);
    }

    private static final class Fixture {
        private final AppState state;
        private final ViewController controller;
        private final AtomicReference<JPanel> form = new AtomicReference<>();
        private final AtomicReference<Path> parent = new AtomicReference<>();
        private final CompletableFuture<String> error = new CompletableFuture<>();

        private Fixture() {
            this(
                    List.of(
                            new Agent("First agent", "first {prompt}"),
                            new Agent("Second agent", "second --prompt {prompt}")));
        }

        private Fixture(final List<Agent> agents) {
            state =
                    new AppState(
                            new AppSettings(agents, "", "light", List.of(), null),
                            Map.of(),
                            Map.of(),
                            Map.of());
            controller = new ViewController(state, () -> null);
        }

        private DialogService dialogs() {
            return (DialogService)
                    Proxy.newProxyInstance(
                            Thread.currentThread().getContextClassLoader(),
                            new Class<?>[] {DialogService.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "show" -> {
                                            form.set((JPanel) args[1]);
                                            yield DialogService.ReturnType.CANCEL;
                                        }
                                        case "chooseDirectory" -> parent.get();
                                        case "errorMessage" -> {
                                            error.complete((String) args[0]);
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected dialog call: "
                                                                + method.getName());
                                    });
        }

        private void open() {
            final var action = new NewProjectAction(controller, state, dialogs());
            assertEquals(
                    "initialize-project",
                    action.id(),
                    "action ID should be distinct from local import");
            action.execute();
        }

        private JTextField folderInput() {
            return component(form.get(), "new-project-folder", JTextField.class);
        }

        private void fill(final String name, final String folder) {
            component(form.get(), "new-project-name", JTextField.class).setText(name);
            folderInput().setText(folder);
            component(form.get(), "new-project-prompt", JTextArea.class).setText(PROMPT);
        }

        private void click(final String name) {
            component(form.get(), name, JButton.class).doClick(0);
        }
    }
}
