package com.jagent.desktop.models;

import java.io.Serializable;

public class Agent implements Serializable {
    private static final long serialVersionUID = 1L;
    public String name;
    public String newSessionCommand;
    public String openCommand;

    public Agent() {
        this("", "", "");
    }

    public Agent(final String name, final String newSessionCommand, final String openCommand) {
        this.name = name;
        this.newSessionCommand = newSessionCommand;
        this.openCommand = openCommand;
    }

    public Agent(final String name, final String command) {
        this(name, command, command.replace("{prompt}", ""));
    }

    @Override
    public String toString() {
        return name;
    }
}
