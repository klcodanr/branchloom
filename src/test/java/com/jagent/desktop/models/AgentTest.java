package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgentTest {
    @Test
    void defaultAgentHasEmptyValues() {
        final Agent agent = new Agent();

        assertEquals("", agent.name, "default agent should have an empty name");
        assertEquals("", agent.newSessionCommand, "default agent should have no session command");
        assertEquals("", agent.openCommand, "default agent should have no open command");
        assertEquals("", agent.toString(), "default agent text should be its name");
    }

    @Test
    void commandConstructorCreatesOpenCommandWithoutPrompt() {
        final Agent agent = new Agent("Codex", "codex --prompt {prompt}");

        assertEquals("Codex", agent.name, "agent name should be retained");
        assertEquals(
                "codex --prompt {prompt}",
                agent.newSessionCommand,
                "session command should retain the prompt placeholder");
        assertEquals(
                "codex --prompt ",
                agent.openCommand,
                "open command should remove the prompt placeholder");
    }

    @Test
    void fullConstructorAllowsDifferentOpenCommand() {
        final Agent agent = new Agent("Claude", "claude {prompt}", "claude");

        assertEquals("Claude", agent.toString(), "agent text should be its name");
        assertEquals(
                "claude {prompt}", agent.newSessionCommand, "session command should be retained");
        assertEquals("claude", agent.openCommand, "open command should be retained");
    }
}
