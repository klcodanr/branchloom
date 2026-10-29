package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.services.BackgroundJobs;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class BackgroundJobDialogTest {
    @Test
    void laysOutJobContextActivityAndOutput() {
        final BackgroundJobs jobs = new BackgroundJobs();
        final var running = jobs.start("Session setup", "Branchloom", "Fix login");
        running.update("Running startup command 2 of 3");
        running.output("Installing dependencies");

        final var content = shownContent(jobs.jobs().getFirst());

        assertNotNull(findLabel(content, "Session setup"), "dialog should show the job title");
        assertNotNull(findLabel(content, "Running"), "dialog should show the running state");
        assertNotNull(findLabel(content, "Project: Branchloom"), "dialog should show the project");
        assertNotNull(findLabel(content, "Session: Fix login"), "dialog should show the session");
        assertNotNull(
                findLabel(content, "Running startup command 2 of 3"),
                "dialog should show current activity");
        assertNotNull(
                findTextArea(content, "Installing dependencies"),
                "dialog should show console output");
        assertNotNull(findButton(content, "Close"), "dialog should provide a close button");
    }

    @Test
    void rendersCompletedAndFailedStates() {
        final BackgroundJobs jobs = new BackgroundJobs();
        final var completed = jobs.start("Completed job");
        completed.complete();
        final var failed = jobs.start("Failed job");
        failed.fail("Command failed");

        assertNotNull(
                findLabel(
                        shownContent(
                                jobs.jobs().stream()
                                        .filter(job -> "Completed job".equals(job.title()))
                                        .findFirst()
                                        .orElseThrow()),
                        "Completed"),
                "dialog should show completed state");
        assertNotNull(
                findLabel(
                        shownContent(
                                jobs.jobs().stream()
                                        .filter(job -> "Failed job".equals(job.title()))
                                        .findFirst()
                                        .orElseThrow()),
                        "Failed"),
                "dialog should show failed state");
    }

    private static JLabel findLabel(final Container container, final String text) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (component instanceof Container child) {
                final JLabel result = findLabel(child, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JTextArea findTextArea(final Container container, final String text) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JTextArea area && text.equals(area.getText())) {
                return area;
            }
            if (component instanceof Container child) {
                final JTextArea result = findTextArea(child, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JButton findButton(final Container container, final String text) {
        for (final Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                final JButton result = findButton(child, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static Container shownContent(final BackgroundJobs.Job job) {
        final AtomicReference<Container> captured = new AtomicReference<>();
        final var dialogs =
                new com.jagent.desktop.services.dialog.DialogService() {
                    @Override
                    public void message(String message, String title, MessageType type) {}

                    @Override
                    public void errorMessage(String message, String title) {}

                    @Override
                    public ReturnType confirm(
                            Object content, String title, OptionType options, MessageType type) {
                        return ReturnType.CANCEL;
                    }

                    @Override
                    public Object input(Object message, String title, MessageType type) {
                        return null;
                    }

                    @Override
                    public ReturnType option(
                            Object message,
                            String title,
                            MessageType type,
                            Object[] options,
                            Object initial) {
                        return ReturnType.CLOSED;
                    }

                    @Override
                    public Path chooseDirectory(String title, Path initial, String approve) {
                        return null;
                    }

                    @Override
                    public ReturnType show(
                            final String title,
                            final javax.swing.JComponent content,
                            final String primary) {
                        captured.set((Container) content);
                        return ReturnType.OK;
                    }
                };
        new BackgroundJobDialog(dialogs).show(job);
        return captured.get();
    }
}
