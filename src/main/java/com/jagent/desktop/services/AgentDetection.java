package com.jagent.desktop.services;

import com.jagent.desktop.models.Agent;
import java.util.ArrayList;
import java.util.List;

/** Detects supported agent command-line tools available on PATH. */
public final class AgentDetection {
    private AgentDetection() {}

    public static List<Agent> detect() {
        final List<Agent> agents = new ArrayList<>();
        addIfAvailable(agents, "Claude Code", "claude", "claude {prompt}", "claude");
        addIfAvailable(agents, "Codex", "codex", "codex {prompt}", "codex");
        addIfAvailable(agents, "Gemini CLI", "gemini", "gemini {prompt}", "gemini");
        addIfAvailable(agents, "Aider", "aider", "aider --message {prompt}", "aider");
        addIfAvailable(agents, "OpenCode", "opencode", "opencode --prompt {prompt}", "opencode");
        return List.copyOf(agents);
    }

    public static String headlessCommand() {
        if (PlatformCommands.commandAvailable("claude")) {
            return "claude -p {prompt}";
        }
        if (PlatformCommands.commandAvailable("codex")) {
            return "codex exec {prompt}";
        }
        if (PlatformCommands.commandAvailable("gemini")) {
            return "gemini -p {prompt}";
        }
        if (PlatformCommands.commandAvailable("aider")) {
            return "aider --message {prompt}";
        }
        if (PlatformCommands.commandAvailable("opencode")) {
            return "opencode run {prompt}";
        }
        return "";
    }

    private static void addIfAvailable(
            final List<Agent> agents,
            final String name,
            final String executable,
            final String newSessionCommand,
            final String openCommand) {
        if (PlatformCommands.commandAvailable(executable)) {
            agents.add(new Agent(name, newSessionCommand, openCommand));
        }
    }
}
