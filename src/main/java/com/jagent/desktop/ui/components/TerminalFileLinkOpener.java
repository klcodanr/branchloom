package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.EditorCommands;
import com.jagent.desktop.services.EditorDetection;
import java.awt.Component;
import java.nio.file.Path;
import javax.swing.JOptionPane;

/** Opens terminal file links in the first available configured editor. */
public final class TerminalFileLinkOpener {
    private TerminalFileLinkOpener() {}

    public static void open(
            final TerminalFileLink link, final Path directory, final Component owner) {
        final var editors = EditorDetection.detect();
        if (editors.isEmpty()) {
            showFailure(owner, "No supported editor is configured.");
            return;
        }
        final Tool editor = editors.getFirst();
        CommandRunner.run(
                EditorCommands.openFile(editor, link.path(), link.line(), link.column()),
                directory,
                null,
                () -> {},
                output ->
                        showFailure(
                                owner,
                                output == null || output.isBlank() ? "Editor failed." : output));
    }

    private static void showFailure(final Component owner, final String message) {
        JOptionPane.showMessageDialog(owner, message, "Open file", JOptionPane.ERROR_MESSAGE);
    }
}
