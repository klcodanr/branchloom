package com.jagent.desktop.services;

import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Owns the in-memory state for sessions while their worktrees are being prepared. */
public final class SessionSetup {
    private final Map<SessionId, Session> sessions = new HashMap<>();
    private final Map<SessionId, SetupProgress> progress = new HashMap<>();
    private final Map<SessionId, List<Consumer<SetupProgress>>> listeners = new HashMap<>();

    public record SetupProgress(String message, boolean complete, boolean failed) {}

    public SessionId begin(final Session session) {
        final SessionId sessionId = SessionId.create();
        sessions.put(sessionId, session);
        progress.put(sessionId, new SetupProgress("Preparing session...", false, false));
        return sessionId;
    }

    public @Nullable Session session(final SessionId sessionId) {
        return sessions.get(sessionId);
    }

    public @Nullable SetupProgress progress(final SessionId sessionId) {
        return progress.get(sessionId);
    }

    public void listen(final SessionId sessionId, final Consumer<SetupProgress> listener) {
        listeners.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(listener);
    }

    public void update(final SessionId sessionId, final String message) {
        update(sessionId, new SetupProgress(message, false, false));
    }

    public void complete(final SessionId sessionId) {
        update(sessionId, new SetupProgress("Setup complete", true, false));
    }

    public void fail(final SessionId sessionId, final String message) {
        update(sessionId, new SetupProgress(message, false, true));
    }

    public SessionId promote(final AppState state, final SessionId setupId, final Session session)
            throws InvalidObjectException {
        if (sessions.remove(setupId) == null) {
            throw new InvalidObjectException("Setup session not found: " + setupId);
        }
        final SessionId persistedSessionId = state.addSession(session.projectId(), session);
        final SetupProgress setupProgress = progress.remove(setupId);
        if (setupProgress != null) {
            progress.put(persistedSessionId, setupProgress);
        }
        final List<Consumer<SetupProgress>> setupListeners = listeners.remove(setupId);
        if (setupListeners != null) {
            listeners.put(persistedSessionId, setupListeners);
        }
        return persistedSessionId;
    }

    private void update(final SessionId sessionId, final SetupProgress setupProgress) {
        if (!sessions.containsKey(sessionId) && !progress.containsKey(sessionId)) {
            return;
        }
        progress.put(sessionId, setupProgress);
        listeners
                .getOrDefault(sessionId, List.of())
                .forEach(listener -> listener.accept(setupProgress));
    }
}
