package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.EditorDetection;
import com.jagent.desktop.services.PlatformCommands;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Locale;
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
                command(editor, link),
                directory,
                null,
                () -> {},
                output ->
                        showFailure(
                                owner,
                                output == null || output.isBlank() ? "Editor failed." : output));
    }

    private static String command(final Tool editor, final TerminalFileLink link) {
        final String executable = editor.command().trim().split("\\s+", 2)[0];
        final Path executablePath = Path.of(executable);
        final Path executableName = executablePath.getFileName();
        final String name =
                (executableName == null ? executable : executableName.toString())
                        .toLowerCase(Locale.ROOT);
        final String path = PlatformCommands.shellQuote(link.path().toString());
        if ("code".equals(name) || "cursor".equals(name)) {
            return executable + " --goto " + path + ":" + link.line() + ":" + link.column();
        }
        if ("nvim".equals(name) || "vim".equals(name)) {
            return executable + " +" + Math.max(1, link.line()) + " " + path;
        }
        return executable + " " + path;
    }

    private static void showFailure(final Component owner, final String message) {
        JOptionPane.showMessageDialog(owner, message, "Open file", JOptionPane.ERROR_MESSAGE);
    }
}
