package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;

class FileViewerUiTest {
    @Test
    void loadsTextFileAndShowsDiff() throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("file-viewer-test");
        final Path file = workspace.resolve("Example.java");
        Files.writeString(file, "class Example {}\n");
        try {
            final FileViewer viewer =
                    GuiActionRunner.execute(() -> new FileViewer(workspace, file));
            waitForStatus(viewer, "Changed");
            assertEquals(
                    "class Example {}\n",
                    source(viewer).getText(),
                    "source viewer should display the file contents");
            assertTrue(
                    diff(viewer).getText().contains("Example.java"),
                    "diff viewer should display the file header");
        } finally {
            delete(workspace);
        }
    }

    @Test
    void reportsBinaryFile() throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("file-viewer-binary-test");
        final Path file = workspace.resolve("data.bin");
        Files.write(file, new byte[] {1, 0, 2});
        try {
            final FileViewer viewer =
                    GuiActionRunner.execute(() -> new FileViewer(workspace, file));
            waitForStatus(viewer, "Binary");
            assertEquals(
                    "Binary file cannot be displayed.",
                    source(viewer).getText(),
                    "binary files should show a readable message");
        } finally {
            delete(workspace);
        }
    }

    private static RSyntaxTextArea source(final FileViewer viewer) {
        return (RSyntaxTextArea)
                ((JScrollPane) ((java.awt.Container) viewer.getComponent(1)).getComponent(0))
                        .getViewport()
                        .getView();
    }

    private static javax.swing.JTextArea diff(final FileViewer viewer) {
        return (javax.swing.JTextArea)
                ((JScrollPane) ((java.awt.Container) viewer.getComponent(1)).getComponent(1))
                        .getViewport()
                        .getView();
    }

    private static void waitForStatus(final FileViewer viewer, final String expected)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (GuiActionRunner.execute(() -> status(viewer).getText().equals(expected))) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("file viewer status did not render: " + expected);
    }

    private static JLabel status(final FileViewer viewer) {
        final java.awt.Container controls =
                (java.awt.Container) ((java.awt.Container) viewer.getComponent(0)).getComponent(1);
        return (JLabel) controls.getComponent(1);
    }

    private static void delete(final Path workspace) throws IOException {
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(FileViewerUiTest::deletePath);
        }
    }

    private static void deletePath(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
