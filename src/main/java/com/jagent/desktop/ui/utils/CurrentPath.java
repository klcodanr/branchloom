package com.jagent.desktop.ui.utils;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;

public final class CurrentPath {
    private CurrentPath() {}

    public static String resolve(final AppState state) {
        final Session session = state.currentSession();
        if (session != null && session.worktreePath() != null) {
            return session.worktreePath();
        }
        final Project project = state.currentProject();
        return project == null ? null : project.path();
    }
}
