package com.jagent.desktop.services.terminal;

public enum TerminalState {
    STARTING("Starting"),
    WORKING("Working"),
    IDLE("No recent output"),
    EXITED("Exited"),
    FAILED("Failed"),
    STOPPED("Stopped");

    private final String label;

    TerminalState(final String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
