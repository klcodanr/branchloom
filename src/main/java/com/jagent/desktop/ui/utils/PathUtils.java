package com.jagent.desktop.ui.utils;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class PathUtils {
    private PathUtils() {}

    public static String resolve(final AppState state) {
        final Session session = state.currentSession();
        if (session != null && session.worktreePath() != null) {
            return session.worktreePath();
        }
        final Project project = state.currentProject();
        return project == null ? null : project.path();
    }

    public static boolean directory(final Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }
}
