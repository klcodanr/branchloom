package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class SetupProgressViewUiTest {
    private static final String DEMO = "Demo";
    private static final String STARTING = "Starting session";
    private static final String PENDING = "Pending";
    private static final String ASSERTION_MESSAGE = "setup progress should match";

    @Test
    void rendersConfiguredStepsAndUpdatesTheirStates() {
        final var view =
                GuiActionRunner.execute(
                        () -> new SetupProgressView(DEMO, List.of("npm install"), true, true));

        assertEquals(
                List.of(
                        STARTING,
                        DEMO,
                        "Create worktree",
                        PENDING,
                        "Run setup: npm install",
                        PENDING,
                        "Start agent",
                        PENDING,
                        ""),
                labels(view),
                ASSERTION_MESSAGE);

        GuiActionRunner.execute(
                () -> {
                    view.start(0);
                    view.complete(1);
                    view.fail(2);
                    view.complete(-1);
                    view.fail(99);
                });

        assertEquals(
                List.of(
                        STARTING,
                        DEMO,
                        "Create worktree",
                        "Running",
                        "Run setup: npm install",
                        "Complete",
                        "Start agent",
                        "Failed",
                        ""),
                labels(view),
                ASSERTION_MESSAGE);
    }

    @Test
    void displaysTrimmedOutputWithOnlyTheLastCharacters() {
        final var view = GuiActionRunner.execute(() -> new SetupProgressView(DEMO, List.of()));
        final String longLine = "x".repeat(200);

        GuiActionRunner.execute(
                () -> {
                    view.output("  output  ");
                    view.output(longLine);
                });

        assertEquals(longLine.substring(20), labels(view).getLast(), ASSERTION_MESSAGE);
        GuiActionRunner.execute(() -> view.output(null));
        assertEquals("", labels(view).getLast(), ASSERTION_MESSAGE);
    }

    @Test
    void retryActionIsAddedOnceAndHidesAfterClick() {
        final var view = GuiActionRunner.execute(() -> new SetupProgressView(DEMO, List.of()));
        final var retries = new AtomicInteger();

        GuiActionRunner.execute(
                () -> {
                    view.failureActions(retries::incrementAndGet, () -> {});
                    view.failureActions(retries::incrementAndGet, () -> {});
                });

        final var retry = findButton(view, "Retry");
        assertTrue(retry.isVisible(), ASSERTION_MESSAGE);
        GuiActionRunner.execute((GuiActionRunnable) retry::doClick);
        assertEquals(1, retries.get(), ASSERTION_MESSAGE);
        assertFalse(retry.isVisible(), ASSERTION_MESSAGE);
    }

    @Test
    void omitsWorktreeAndAgentStepsWhenDisabled() {
        final var view =
                GuiActionRunner.execute(() -> new SetupProgressView(DEMO, List.of(), false, false));

        assertEquals(List.of(STARTING, DEMO, ""), labels(view), ASSERTION_MESSAGE);
    }

    @Test
    void supportsOnlySetupCommandsWhenWorktreeAndAgentAreDisabled() {
        final var view =
                GuiActionRunner.execute(
                        () -> new SetupProgressView(DEMO, List.of("one", "two"), false, false));

        assertEquals(
                List.of(STARTING, DEMO, "Run setup: one", PENDING, "Run setup: two", PENDING, ""),
                labels(view),
                ASSERTION_MESSAGE);

        GuiActionRunner.execute(
                () -> {
                    view.start(0);
                    view.complete(1);
                });

        assertEquals(
                List.of(
                        STARTING,
                        DEMO,
                        "Run setup: one",
                        "Running",
                        "Run setup: two",
                        "Complete",
                        ""),
                labels(view),
                ASSERTION_MESSAGE);
    }

    private static List<String> labels(final JPanel panel) {
        return java.util.Arrays.stream(panel.getComponents())
                .flatMap(
                        component ->
                                component instanceof JLabel label
                                        ? java.util.stream.Stream.of(label.getText())
                                        : component instanceof JPanel child
                                                ? labels(child).stream()
                                                : java.util.stream.Stream.empty())
                .toList();
    }

    private static JButton findButton(final JPanel panel, final String text) {
        for (final var component : panel.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof JPanel child) {
                try {
                    return findButton(child, text);
                } catch (final AssertionError ignored) {
                    // Continue searching sibling panels.
                }
            }
        }
        throw new AssertionError("button not found: " + text);
    }
}
