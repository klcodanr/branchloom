package com.jagent.desktop.models;

public record Terminal(TerminalId id, IdType owner, String title, String command) {

    public static Terminal create(final IdType owner, final String title, final String command) {
        return new Terminal(TerminalId.create(), owner, title, command);
    }

    public Terminal withTitle(final String updatedTitle) {
        return new Terminal(id, owner, updatedTitle, command);
    }
}
