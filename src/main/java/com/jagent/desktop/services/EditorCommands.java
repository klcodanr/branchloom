package com.jagent.desktop.services;

import com.jagent.desktop.models.Tool;
import java.nio.file.Path;
import java.util.Locale;

/** Builds commands for opening files in supported editors. */
public final class EditorCommands {
    private EditorCommands() {}

    public static String openFile(
            final Tool editor, final Path file, final int line, final int column) {
        final String executable = editor.command().trim().split("\\s+", 2)[0];
        final Path executablePath = Path.of(executable);
        final Path executableName = executablePath.getFileName();
        final String name =
                (executableName == null ? executable : executableName.toString())
                        .toLowerCase(Locale.ROOT);
        final String path =
                PlatformCommands.shellQuote(file.toAbsolutePath().normalize().toString());
        if ("code".equals(name) || "cursor".equals(name)) {
            return executable + " --goto " + path + ":" + line + ":" + column;
        }
        if ("nvim".equals(name) || "vim".equals(name)) {
            return executable + " +" + Math.max(1, line) + " " + path;
        }
        return executable + " " + path;
    }
}
