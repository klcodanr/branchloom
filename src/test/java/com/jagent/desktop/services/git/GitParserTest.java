package com.jagent.desktop.services.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.Git;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitParserTest {
    @Test
    void parsesLocalAndRemoteBranchesAndSkipsHeadReferences() {
        final List<Git.Branch> branches =
                GitParser.parseBranches(
                        List.of(
                                "refs/heads/main|",
                                "refs/heads/main|*",
                                "refs/remotes/origin/feature|",
                                "refs/remotes/origin/HEAD|",
                                "|"));

        assertEquals(
                List.of(new Git.Branch("main", false), new Git.Branch("origin/feature", true)),
                branches,
                "local and remote branches should exclude HEAD references");
    }

    @Test
    void parsesStatusAndRenameEntries() {
        final Map<String, String> statuses =
                GitParser.parseStatus(" M changed.txt\0R  old.txt\0renamed.txt\0?? new.txt\0");

        assertEquals(" M", statuses.get("changed.txt"), "modified status should be preserved");
        assertEquals("R ", statuses.get("old.txt"), "rename status should be preserved");
        assertEquals("??", statuses.get("new.txt"), "untracked status should be preserved");
        assertFalse(statuses.containsKey("renamed.txt"), "rename metadata should be consumed");
        assertEquals(
                Map.of("new.txt", "A", "old.txt", "D"),
                GitParser.parseDiffStatus("A\0new.txt\0D\0old.txt\0"),
                "diff status should map paths to status codes");
    }

    @Test
    void calculatesWorktreeStatus() {
        final Git.WorktreeStatus status =
                GitParser.parseWorktreeStatus(
                        "A  added.txt\0 D deleted.txt\0 M modified.txt\0?? untracked.txt\0");

        assertEquals(4, status.files().size(), "all worktree entries should be retained");
        assertEquals(2, status.additions(), "added and untracked files should count as additions");
        assertEquals(1, status.deletions(), "deleted files should count as deletions");
        assertEquals(1, status.modifications(), "modified files should count as modifications");
    }

    @Test
    void parsesWorktreesAndPrunableEntries() {
        final List<Git.Worktree> worktrees =
                GitParser.parseWorktrees(
                        List.of(
                                "worktree one",
                                "branch refs/heads/main",
                                "worktree two",
                                "branch refs/heads/feature",
                                "prunable gone"));

        assertEquals(2, worktrees.size(), "worktree records should be grouped from the input");
        assertEquals(Path.of("one"), worktrees.get(0).path(), "first worktree path should parse");
        assertEquals(
                "refs/heads/main", worktrees.get(0).branch(), "first worktree branch should parse");
        assertFalse(worktrees.get(0).prunable(), "normal worktrees should not be prunable");
        assertTrue(worktrees.get(1).prunable(), "prunable marker should be retained");
    }
}
