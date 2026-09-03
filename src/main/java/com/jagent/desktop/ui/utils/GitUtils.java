package com.jagent.desktop.ui.utils;

import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

public final class GitUtils {
    private GitUtils() {}

    public static String toBranchSlug(final String input) {
        if (input == null || input.isBlank()) {
            return "branch";
        }

        final String slug =
                Normalizer.normalize(input, Normalizer.Form.NFKD)
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-+|-+$", "");

        if (slug.isEmpty()) {
            return "branch";
        }
        return slug.length() > 100 ? slug.substring(0, 100).replaceFirst("-+$", "") : slug;
    }

    public static boolean isWorktreeRegistered(
            final Map<SessionId, Session> sessions, final Path worktree) {
        final Path normalized = worktree.toAbsolutePath().normalize();
        return sessions.values().stream()
                .map(Session::worktreePath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> Path.of(path).toAbsolutePath().normalize())
                .anyMatch(normalized::equals);
    }
}
