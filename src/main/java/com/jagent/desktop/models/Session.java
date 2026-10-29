package com.jagent.desktop.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.Nullable;

public record Session(
        SessionId id,
        ProjectId projectId,
        String name,
        @Nullable String agent,
        @Nullable String prompt,
        @Nullable String worktreePath,
        Instant created,
        List<TerminalId> terminalIds) {

    public Session {
        terminalIds = List.copyOf(Optional.ofNullable(terminalIds).orElse(List.of()));
    }

    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public static Session create(
            final ProjectId projectId,
            @Nonnull final String name,
            @Nullable final String agent,
            @Nullable final String prompt,
            @Nullable final String worktreePath) {
        return new Session(
                SessionId.create(),
                projectId,
                name,
                agent,
                prompt,
                worktreePath,
                Instant.now(),
                List.of());
    }

    public Session withName(final String name) {
        return new Session(
                this.id,
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
                this.id,
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
                this.id,
                this.projectId,
                this.name,
                this.agent,
                this.prompt,
                this.worktreePath,
                this.created,
                this.terminalIds.stream().filter(tid -> !tid.equals(terminalId)).toList());
    }
}
