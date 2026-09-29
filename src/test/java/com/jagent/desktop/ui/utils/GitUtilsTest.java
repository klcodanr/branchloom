package com.jagent.desktop.ui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitUtilsTest {
    @Test
    void branchSlugsNormalizeInputAndHaveFallbacks() {
        assertEquals("branch", GitUtils.toBranchSlug(null), "null should use fallback");
        assertEquals("branch", GitUtils.toBranchSlug("  "), "blank should use fallback");
        assertEquals(
                "cafe-au-lait", GitUtils.toBranchSlug("Café au lait"), "text should normalize");
        assertEquals("branch", GitUtils.toBranchSlug("!!!"), "punctuation should use fallback");
        assertTrue(
                GitUtils.toBranchSlug("a".repeat(120)).length() <= 100, "slug should be bounded");
    }

    @Test
    void worktreeRegistrationIgnoresBlankPaths() {
        final ProjectId projectId = ProjectId.create();
        final SessionId sessionId = SessionId.create();
        final String worktree = Path.of(System.getProperty("java.io.tmpdir"), "work").toString();
        final Map<SessionId, Session> sessions = new HashMap<>();
        sessions.put(sessionId, new Session(projectId, "Feature", "agent", "prompt", worktree));
        sessions.put(SessionId.create(), new Session(projectId, "No path", null, null, null));
        sessions.put(SessionId.create(), new Session(projectId, "Blank", null, null, "  "));

        final Path worktreePath = Path.of(worktree);
        final Path other = Path.of(System.getProperty("java.io.tmpdir"), "other");
        assertTrue(
                GitUtils.isWorktreeRegistered(sessions, worktreePath),
                "matching worktree should be detected");
        assertTrue(
                !GitUtils.isWorktreeRegistered(sessions, other),
                "different or blank worktrees should not match");
    }
}
