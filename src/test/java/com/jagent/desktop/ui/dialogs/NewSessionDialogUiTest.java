package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class NewSessionDialogUiTest {
    @Test
    void enteringSessionDetailsAndAcceptingReportsRequest() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final Agent agent = new Agent("Test agent", "agent {prompt}");
        final var settings = Defaults.appSettings();
        final AppState state =
                new AppState(
                        new com.jagent.desktop.models.AppSettings(
                                List.of(agent),
                                settings.groupOrder(),
                                settings.reviewPrompt(),
                                settings.theme(),
                                settings.tools(),
                                settings.worktreeTemplate()),
                        Map.of(),
                        Map.of(),
                        Map.of());
        final AtomicReference<NewSessionDialog.Request> request = new AtomicReference<>();
        final Robot robot = BasicRobot.robotWithNewAwtHierarchy();
        final JFrame owner = GuiActionRunner.execute(() -> new JFrame());
        GuiActionRunner.execute(() -> owner.setVisible(true));
        final NewSessionDialog dialog =
                GuiActionRunner.execute(
                        () ->
                                new NewSessionDialog(
                                        new ActionContext(new ViewCoordinator(state), state, owner),
                                        request::set));
        final DialogFixture fixture = new DialogFixture(robot, dialog);

        try {
            fixture.show();
            final var nameField = fixture.textBox("session-name");
            final var promptField =
                    fixture.textBox(
                            new GenericTypeMatcher<>(JTextArea.class) {
                                @Override
                                protected boolean isMatching(final JTextArea component) {
                                    return "session-prompt".equals(component.getName());
                                }
                            });
            GuiActionRunner.execute(
                    () -> {
                        nameField.target().setText(" Demo session ");
                        promptField.target().setText("Run the tests");
                    });
            nameField.requireText(" Demo session ");
            promptField.requireText("Run the tests");
            GuiActionRunner.execute(() -> dialog.getRootPane().getDefaultButton().doClick());

            assertNotNull(request.get(), "accepting the dialog should submit a request");
            assertEquals("Demo session", request.get().name(), "session name should be trimmed");
            assertEquals(agent, request.get().agent(), "selected agent should be submitted");
            assertEquals("Run the tests", request.get().prompt(), "prompt should be submitted");
        } finally {
            fixture.cleanUp();
            owner.dispose();
            robot.cleanUp();
        }
    }
}
