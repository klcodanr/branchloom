package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JTextField;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class CommandPaletteUiTest {
    @Test
    void searchingAndOpeningACommandRunsTheSelectedAction() throws InterruptedException {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final AtomicBoolean opened = new AtomicBoolean();
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Robot robot = BasicRobot.robotWithNewAwtHierarchy();
        final JFrame owner = GuiActionRunner.execute(() -> new JFrame());
        GuiActionRunner.execute(() -> owner.setVisible(true));

        final Thread opener =
                new Thread(
                        () -> {
                            try {
                                CommandPalette.open(
                                        owner,
                                        "Commands",
                                        List.of(
                                                new CommandPalette.Choice("Open project", () -> {}),
                                                new CommandPalette.Choice(
                                                        "Open settings", () -> opened.set(true))));
                            } catch (final Throwable exception) {
                                failure.set(exception);
                            } finally {
                                finished.countDown();
                            }
                        });
        opener.start();

        try {
            final JDialog dialog =
                    robot.finder()
                            .find(
                                    new GenericTypeMatcher<>(JDialog.class) {
                                        @Override
                                        protected boolean isMatching(final JDialog candidate) {
                                            return "Commands".equals(candidate.getTitle());
                                        }
                                    });
            final DialogFixture fixture = new DialogFixture(robot, dialog);
            final JTextField search =
                    robot.finder()
                            .find(
                                    new GenericTypeMatcher<>(JTextField.class) {
                                        @Override
                                        protected boolean isMatching(final JTextField candidate) {
                                            return candidate.getParent() != null
                                                    && candidate.isShowing();
                                        }
                                    });
            final JList<?> results =
                    robot.finder()
                            .find(
                                    new GenericTypeMatcher<>(JList.class) {
                                        @Override
                                        protected boolean isMatching(final JList candidate) {
                                            return candidate.isShowing();
                                        }
                                    });

            GuiActionRunner.execute(() -> search.setText(" SETTINGS "));
            GuiActionRunner.execute(() -> {});
            assertEquals(1, results.getModel().getSize(), "search should filter the command list");
            assertEquals(
                    "Open settings",
                    results.getModel().getElementAt(0).toString(),
                    "filtered results should contain the matching command");

            final JButton open =
                    robot.finder()
                            .find(
                                    new GenericTypeMatcher<>(JButton.class) {
                                        @Override
                                        protected boolean isMatching(final JButton candidate) {
                                            return "Open".equals(candidate.getText())
                                                    && candidate.isShowing();
                                        }
                                    });
            fixture.requireVisible();
            GuiActionRunner.execute((GuiActionRunnable) open::doClick);

            assertTrue(finished.await(5, TimeUnit.SECONDS), "palette should close after opening");
            assertTrue(opened.get(), "opening the selected command should run its action");
            assertTrue(failure.get() == null, "palette should not fail: " + failure.get());
        } finally {
            if (opener.isAlive()) {
                GuiActionRunner.execute(
                        () -> {
                            for (final java.awt.Window window : java.awt.Window.getWindows()) {
                                if (window instanceof JDialog dialog
                                        && "Commands".equals(dialog.getTitle())) {
                                    dialog.dispose();
                                }
                            }
                        });
            }
            owner.dispose();
            robot.cleanUp();
        }
    }
}
