package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Tool;
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
            JOptionPane.showMessageDialog(
                    owner,
                    "No supported editor is configured.",
                    "Open file",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        final Tool editor = editors.getFirst();
        BackgroundOperations.runCommand(
                        "Commands",
                        "open-file-link",
                        EditorCommands.openFile(editor, link.path(), link.line(), link.column()),
                        directory,
                        null)
                .exceptionally(
                        exception -> {
                            final String message =
                                    exception.getCause() == null
                                            ? exception.getMessage()
                                            : exception.getCause().getMessage();
                            JOptionPane.showMessageDialog(
                                    owner,
                                    UiText.valueOrDefault(message, "Editor failed."),
                                    "Open file",
                                    JOptionPane.ERROR_MESSAGE);
                            return null;
                        });
    }
}
