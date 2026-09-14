package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.services.BackgroundJobs;
import java.awt.Component;
import java.awt.Container;
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

        final var content = BackgroundJobDialog.content(jobs.jobs().getFirst(), null);

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
                        BackgroundJobDialog.content(
                                jobs.jobs().stream()
                                        .filter(job -> "Completed job".equals(job.title()))
                                        .findFirst()
                                        .orElseThrow(),
                                null),
                        "Completed"),
                "dialog should show completed state");
        assertNotNull(
                findLabel(
                        BackgroundJobDialog.content(
                                jobs.jobs().stream()
                                        .filter(job -> "Failed job".equals(job.title()))
                                        .findFirst()
                                        .orElseThrow(),
                                null),
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
}
