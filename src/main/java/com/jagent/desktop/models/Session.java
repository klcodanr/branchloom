package com.jagent.desktop.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public record Session(
        ProjectId projectId,
        String name,
        @Nullable String agent,
        @Nullable String prompt,
        @Nullable String worktreePath,
        Instant created,
        List<TerminalId> terminalIds) {

    public Session {
        terminalIds = terminalIds == null ? List.of() : List.copyOf(terminalIds);
    }

    public Session(
            final ProjectId projectId,
            final String name,
            final String agent,
            final String prompt,
            final String worktreePath) {
        this(projectId, name, agent, prompt, worktreePath, Instant.now(), List.of());
    }

    public Session withName(final String name) {
        return new Session(
                this.projectId,
                name,
                this.agent,
                this.prompt,
                this.worktreePath,
                this.created,
                this.terminalIds);
    }

    public Session withNewTerminal(final TerminalId terminalId) {
        final var newTerminals = new ArrayList<TerminalId>(this.terminalIds);
        newTerminals.add(terminalId);
        return new Session(
                this.projectId,
                this.name,
                this.agent,
                this.prompt,
                this.worktreePath,
                this.created,
                newTerminals);
    }

    public Session withRemovedTerminal(final TerminalId terminalId) {
        return new Session(
                this.projectId,
                this.name,
                this.agent,
                this.prompt,
                this.worktreePath,
                this.created,
                this.terminalIds.stream().filter(tid -> !tid.equals(terminalId)).toList());
    }
}
