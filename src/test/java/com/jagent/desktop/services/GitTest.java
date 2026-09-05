package com.jagent.desktop.services;

import static com.jagent.desktop.test.TestGitRepository.output;
import static com.jagent.desktop.test.TestGitRepository.run;
import static org.junit.jupiter.api.Assertions.*;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.test.TestGitRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitTest {
    private static final String TRACKED_FILE = "tracked.txt";
    private static final String SESSION_NAME = "session";
    private static final String FEATURE_ARGUMENT = " feature";
    private static final String FEATURE_BRANCH = "feature";
    private static final String BRANCH_COMMAND = "git branch ";
    private static final String WORKTREE_BRANCH_COMMAND =
            BRANCH_COMMAND + FEATURE_BRANCH + " && git worktree add -q ";
    private static final String SHOW_BRANCH_COMMAND = "git branch --show-current";

    @Test
    void githubCommandUsesSelectedAccount() {
        final Project project =
                new Project(
                        "Demo",
                        "/tmp/demo",
                        null,
                        "github.example",
                        "user",
                        null,
                        null,
                        List.of(),
                        List.of());

        assertEquals(
                "GH_TOKEN=$(gh auth token --hostname 'github.example' --user 'user') git status",
                Git.githubCommand(project, "git status"),
                "configured GitHub account should be applied");
        assertEquals(
                "git status",
                Git.githubCommand(new Project("Demo", "/tmp/demo", null), "git status"),
                "missing GitHub account should leave command unchanged");
    }

    @Test
    void quotesWindowsCommandArguments() {
        assertEquals(
                "\"prompt with ^^^& ^| ^< ^> ^( ^) ^% ^! and \\\"quotes\\\"\"",
                PlatformCommands.windowsShellQuote("prompt with ^& | < > ( ) % ! and \"quotes\""),
                "Windows command arguments should escape shell metacharacters");
    }

    @Test
    void repositoryValidationUsesGit(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);

        assertTrue(Git.isRepository(directory), "initialized repository should validate");
        final Path plain = directory.resolveSibling(directory.getFileName() + "-plain");
        Files.createDirectory(plain);
        final Path file = plain.resolve("file");
        Files.writeString(file, "not a repository");
        assertFalse(
                Git.isRepository(plain),
                "ordinary directories should not validate as repositories");
        assertFalse(Git.isRepository(file), "ordinary files should not validate as repositories");
        assertFalse(
                Git.isRepository(directory.resolve("missing")),
                "missing directory should not validate as a repository");
    }

    @Test
    void statusFilesReadsModifiedAndUntrackedFiles(@TempDir final Path directory)
            throws IOException, InterruptedException {
        run(directory, "git init -q");
        Files.writeString(directory.resolve(TRACKED_FILE), "before");
        run(
                directory,
                "git add tracked.txt && git -c user.name=test -c user.email=test commit -qm initial");
        Files.writeString(directory.resolve(TRACKED_FILE), "after");
        Files.writeString(directory.resolve("new.txt"), "new");

        final Map<String, String> statuses = Git.statusFiles(directory);

        assertEquals(" M", statuses.get("tracked.txt"), "modified file status should be read");
        assertTrue(statuses.containsKey("new.txt"), "untracked file should be read");
    }

    @Test
    void worktreeStatusCanIncludeChangesSinceUpstream(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        run(
                directory,
                "git checkout -qb feature && printf 'feature' > feature.txt && git add feature.txt"
                        + " && git commit -qm feature && git update-ref refs/remotes/origin/master"
                        + " HEAD~1 && git symbolic-ref refs/remotes/origin/HEAD refs/remotes/origin/master"
                        + " && git config branch.feature.remote origin"
                        + " && git config branch.feature.merge refs/heads/master");
        Files.writeString(directory.resolve(TRACKED_FILE), "local");

        final Git.WorktreeStatus local = Git.worktreeStatus(directory, false);
        final Git.WorktreeStatus includingSource = Git.worktreeStatus(directory, true);

        assertFalse(
                local.files().containsKey("feature.txt"), "local status should exclude commits");
        assertTrue(
                includingSource.files().containsKey("feature.txt"),
                "source comparison should include committed branch changes");
        assertTrue(
                includingSource.files().containsKey(TRACKED_FILE),
                "source comparison should retain local worktree changes");
    }

    @Test
    void worktreeStatusUsesConfiguredUpstream(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        run(
                directory,
                "git checkout -qb feature && printf 'feature' > feature.txt && git add feature.txt"
                        + " && git commit -qm feature && git update-ref refs/remotes/origin/master HEAD~1"
                        + " && git config branch.feature.remote origin"
                        + " && git config branch.feature.merge refs/heads/master");

        final Git.WorktreeStatus status = Git.worktreeStatus(directory, true);

        assertNotNull(status, "configured upstream status should be returned");
    }

    @Test
    void statusReportsCleanAndModifiedRepositories(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);

        assertEquals("", Git.status(directory), "clean repositories should have empty status");
        Files.writeString(directory.resolve(TRACKED_FILE), "changed");
        assertEquals(
                " M tracked.txt\n",
                Git.status(directory),
                "modified repositories should report changed files");
    }

    @Test
    void worktreeStatusSkipsSourceChangesWithoutAnUpstream(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        run(
                directory,
                "git checkout -qb feature && printf 'feature' > feature.txt && git add feature.txt"
                        + " && git commit -qm feature");
        Files.writeString(directory.resolve(TRACKED_FILE), "local");

        final Git.WorktreeStatus status = Git.worktreeStatus(directory, true);

        assertTrue(status.files().containsKey(TRACKED_FILE), "local changes should be retained");
        assertFalse(
                status.files().containsKey("feature.txt"),
                "committed changes should be skipped without a source branch");
    }

    @Test
    void statusFilesHandlesRenameAndCopyPairs(@TempDir final Path directory)
            throws IOException, InterruptedException {
        run(directory, "git init -q");
        Files.writeString(directory.resolve("original.txt"), "content");
        Files.writeString(directory.resolve("source.txt"), "copy content");
        run(directory, "git add -A && git -c user.name=test -c user.email=test commit -qm initial");
        run(directory, "git mv original.txt renamed.txt");
        Files.copy(directory.resolve("source.txt"), directory.resolve("copied.txt"));
        run(directory, "git config status.renames copies && git add -A");

        final Map<String, String> statuses = Git.statusFiles(directory);

        assertEquals("R ", statuses.get("renamed.txt"), "rename status should be reported");
        assertEquals("A ", statuses.get("copied.txt"), "copy status should be reported");
        assertFalse(
                statuses.containsKey("original.txt"),
                "original path should be absent after rename");
    }

    @Test
    void listBranchesFiltersRemoteHeadAndSymbolicRefs(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        run(directory, "git branch local && " + BRANCH_COMMAND + FEATURE_BRANCH);
        run(directory, "git update-ref refs/remotes/origin/feature HEAD");
        run(directory, "git symbolic-ref refs/remotes/origin/HEAD refs/remotes/origin/feature");

        final Project project = project(directory);
        final List<Git.Branch> branches = new Git().listBranches(project).join();

        assertTrue(
                branches.contains(new Git.Branch("local", false)), "local branch should be listed");
        assertTrue(
                branches.contains(new Git.Branch(FEATURE_BRANCH, false)),
                "feature branch should be listed");
        assertTrue(
                branches.contains(new Git.Branch("origin/feature", true)),
                "remote branch should be listed");
        assertFalse(
                branches.stream().anyMatch(branch -> branch.name().endsWith("/HEAD")),
                "remote HEAD should be excluded");
        assertEquals(
                3,
                new Git().listLocalBranches(project).join().size(),
                "local branch count should match");
    }

    @Test
    void branchExistsReportsLocalBranchOnly(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        run(
                directory,
                BRANCH_COMMAND
                        + FEATURE_BRANCH
                        + " && git update-ref refs/remotes/origin/remote-only HEAD");
        final Git git = new Git();

        assertTrue(
                git.branchExists(project(directory), FEATURE_BRANCH).join(),
                "local branch should exist");
        assertFalse(
                git.branchExists(project(directory), "missing").join(),
                "missing branch should not exist");
        assertFalse(
                git.branchExists(project(directory), "origin/remote-only").join(),
                "remote-only branch should not count");
    }

    @Test
    void availableBranchesExcludesBranchesAlreadyUsedByWorktrees(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        run(directory, BRANCH_COMMAND + FEATURE_BRANCH + " && " + BRANCH_COMMAND + "other");
        final Path worktree = directory.resolveSibling(directory.getFileName() + "-feature");
        run(
                directory,
                "git worktree add -q "
                        + PlatformCommands.shellQuote(worktree.toString())
                        + FEATURE_ARGUMENT);

        final List<Git.Branch> available =
                new Git().listAvailableBranches(project(directory)).join();

        assertTrue(
                available.stream().noneMatch(branch -> FEATURE_BRANCH.equals(branch.name())),
                "used branch should be excluded");
        assertTrue(
                available.stream().anyMatch(branch -> "other".equals(branch.name())),
                "unused branch should be available");
    }

    @Test
    void listWorktreesReportsDetachedAndPrunableEntries(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Path repository = directory.toRealPath();
        final Path attached = repository.resolveSibling(repository.getFileName() + "-attached");
        final Path prunable = repository.resolveSibling(repository.getFileName() + "-prunable");
        run(directory, BRANCH_COMMAND + FEATURE_BRANCH);
        run(
                directory,
                "git worktree add -q "
                        + PlatformCommands.shellQuote(attached.toString())
                        + FEATURE_ARGUMENT);
        run(
                directory,
                "git worktree add -q --detach " + PlatformCommands.shellQuote(prunable.toString()));
        Files.delete(prunable.resolve(TRACKED_FILE));
        Files.delete(prunable.resolve(".git"));
        Files.delete(prunable);

        final List<Path> worktrees = new Git().listWorktrees(project(repository)).join();

        assertTrue(worktrees.contains(repository), worktrees.toString());
        assertTrue(worktrees.contains(attached), worktrees.toString());
        assertTrue(
                new Git().checkPrunableWorktree(project(repository), prunable).join().isPresent(),
                "prunable worktree should be detected");
    }

    @Test
    void validateWorktreeDeletionRejectsUnsafeAndUnregisteredPaths(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Project project = project(directory);
        final Git git = new Git();
        final Path unregistered = directory.resolve("unregistered");
        Files.createDirectory(unregistered);

        assertThrows(
                IOException.class,
                () -> git.validateWorktreeDeletion(project, session(directory)),
                "repository root should not be deletable");
        assertThrows(
                IOException.class,
                () ->
                        git.validateWorktreeDeletion(
                                project,
                                new Session(
                                        null, SESSION_NAME, null, null, unregistered.toString())),
                "unregistered directory should not be deletable");
        assertThrows(
                IOException.class,
                () ->
                        git.validateWorktreeDeletion(
                                project,
                                new Session(null, SESSION_NAME, null, null, directory.toString())),
                "repository root should not be deletable");
    }

    @Test
    void validateWorktreeDeletionRejectsMissingAndSymbolicLinkPaths(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Git git = new Git();
        final Project project = project(directory);
        final Path missing = directory.resolveSibling(directory.getFileName() + "-missing");
        final Path target = directory.resolveSibling(directory.getFileName() + "-target");
        final Path link = directory.resolveSibling(directory.getFileName() + "-link");
        Files.createDirectory(target);
        Files.createSymbolicLink(link, target);

        assertThrows(
                IOException.class,
                () -> git.validateWorktreeDeletion(project, session(missing)),
                "missing worktree should not be deletable");
        assertThrows(
                IOException.class,
                () -> git.validateWorktreeDeletion(project, session(link)),
                "symbolic-link worktree should not be deletable");
    }

    @Test
    void validateWorktreeDeletionRejectsBlankAndRepositoryRootPaths(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Git git = new Git();
        final Project project = project(directory);
        final Path root = directory.toAbsolutePath().getRoot();

        assertThrows(
                IOException.class,
                () ->
                        git.validateWorktreeDeletion(
                                project, new Session(null, SESSION_NAME, null, null, " ")),
                "blank worktree path should not be deletable");
        assertThrows(
                IOException.class,
                () -> git.validateWorktreeDeletion(project, session(root)),
                "filesystem root should not be deletable");
    }

    @Test
    void validateWorktreeDeletionAcceptsRegisteredDirectory(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Path repository = directory.toRealPath();
        final Path worktree = repository.resolveSibling(repository.getFileName() + "-valid");
        run(
                directory,
                WORKTREE_BRANCH_COMMAND
                        + PlatformCommands.shellQuote(worktree.toString())
                        + FEATURE_ARGUMENT);

        assertEquals(
                worktree.toAbsolutePath().normalize(),
                new Git().validateWorktreeDeletion(project(repository), session(worktree)),
                "registered worktree should validate");
    }

    @Test
    void restoreWorktreeRecreatesStaleBranchWorktree(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Path repository = directory.toRealPath();
        final Path worktree = repository.resolveSibling(repository.getFileName() + "-restored");
        run(
                directory,
                WORKTREE_BRANCH_COMMAND
                        + PlatformCommands.shellQuote(worktree.toString())
                        + FEATURE_ARGUMENT);
        run(
                directory,
                "git worktree remove --force " + PlatformCommands.shellQuote(worktree.toString()));

        new Git()
                .restoreWorktree(
                        project(repository), new Git.Worktree(worktree, "refs/heads/feature", true))
                .join();

        assertTrue(Files.isDirectory(worktree), "restored worktree should be a directory");
        assertEquals(
                "feature\n",
                readCommand(worktree, SHOW_BRANCH_COMMAND),
                "restored worktree should use its branch");
    }

    @Test
    void restoreWorktreeReportsMissingBranchAndCommandFailure(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Git git = new Git();
        final Project project = project(directory);

        assertCompletionFailure(
                () ->
                        git.restoreWorktree(
                                        project,
                                        new Git.Worktree(directory.resolve("stale"), null, true))
                                .join());
        assertCompletionFailure(
                () ->
                        git.restoreWorktree(
                                        project,
                                        new Git.Worktree(
                                                directory.resolve("stale"),
                                                "refs/heads/missing",
                                                true))
                                .join());
    }

    @Test
    void deleteWorktreeRemovesRegisteredWorktree(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Path worktree = directory.resolveSibling(directory.getFileName() + "-deleted");
        run(
                directory,
                WORKTREE_BRANCH_COMMAND
                        + PlatformCommands.shellQuote(worktree.toString())
                        + FEATURE_ARGUMENT);

        new Git().deleteWorktree(project(directory), worktree);

        assertFalse(Files.exists(worktree), "deleted worktree should be absent");
    }

    @Test
    void deleteWorktreeReportsGitFailure(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                new Git()
                                        .deleteWorktree(
                                                project(directory), directory.resolve("missing")));

        assertFalse(exception.getMessage().isBlank(), "git failure should include a message");
    }

    @Test
    void readsCurrentBranchAndWorktreeDiffSummary(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        Files.writeString(directory.resolve(TRACKED_FILE), "updated");
        Files.writeString(directory.resolve("untracked.txt"), "new file\n");

        assertEquals("master\n", Git.currentBranch(directory), "current branch should be reported");
        assertEquals(
                "1\t1\ttracked.txt\n1\t0\tuntracked.txt\n",
                Git.diffSummary(directory),
                "diff summary should report tracked and untracked worktree changes");
    }

    @Test
    void createsAndAddsWorktreesWithRealGit(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Project project = project(directory);
        final Path created = directory.resolveSibling(directory.getFileName() + "-created");
        final Path added = directory.resolveSibling(directory.getFileName() + "-added");

        new Git().createWorktree(project, "created", created).join();
        new Git().addWorktree(project, "master", added, true, "added").join();

        assertEquals(
                "created\n",
                readCommand(created, SHOW_BRANCH_COMMAND),
                "created worktree should use its branch");
        assertEquals(
                "added\n",
                readCommand(added, SHOW_BRANCH_COMMAND),
                "added worktree should use its branch");
        assertTrue(
                Files.exists(created.resolve(TRACKED_FILE)),
                "created worktree should contain files");
        assertTrue(
                Files.exists(added.resolve(TRACKED_FILE)), "added worktree should contain files");
    }

    @Test
    void clonesRepositoryToDestination(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Path source = directory.resolve("source");
        Files.createDirectory(source);
        TestGitRepository.initialize(source);
        final Path destination = directory.resolve("destination");

        new Git().cloneRepository(source.toString(), destination).join();

        assertTrue(Files.isDirectory(destination), "clone destination should be created");
        assertTrue(Files.exists(destination.resolve(TRACKED_FILE)), "cloned files should exist");
        assertTrue(Git.isRepository(destination), "clone destination should be a repository");
    }

    @Test
    void createsWorktreeFromAPlainBaseReference(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Path worktree = directory.resolveSibling(directory.getFileName() + "-base");

        new Git().createWorktree(project(directory), "created", worktree, "master").join();

        assertEquals(
                "created\n",
                readCommand(worktree, SHOW_BRANCH_COMMAND),
                "worktree should be created from a plain local base reference");

        assertCompletionFailure(
                () ->
                        new Git()
                                .createWorktree(
                                        project(directory),
                                        "remote-created",
                                        directory.resolveSibling(
                                                directory.getFileName() + "-remote"),
                                        "origin/master")
                                .join());
    }

    @Test
    void reportsFailedPullAndPullRequestFetch(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Git git = new Git();
        final Project project = project(directory);

        assertCompletionFailure(() -> git.fetchPullRequest(project, 12).join());
        assertCompletionFailure(() -> git.updateCurrentBranch(project).join());
    }

    @Test
    void addsAnExistingBranchWithoutCreatingANewBranch(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        run(directory, BRANCH_COMMAND + FEATURE_BRANCH);
        final Path worktree = directory.resolveSibling(directory.getFileName() + "-existing");

        new Git()
                .addWorktree(project(directory), FEATURE_BRANCH, worktree, false, "ignored")
                .join();

        assertEquals(
                "feature\n",
                readCommand(worktree, SHOW_BRANCH_COMMAND),
                "existing branch worktree should preserve its branch");
    }

    @Test
    void prunesDeletedWorktrees(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Path worktree = directory.resolveSibling(directory.getFileName() + "-prune");
        run(
                directory,
                WORKTREE_BRANCH_COMMAND
                        + PlatformCommands.shellQuote(worktree.toString())
                        + FEATURE_ARGUMENT);
        Files.delete(worktree.resolve(TRACKED_FILE));
        Files.delete(worktree.resolve(".git"));
        Files.delete(worktree);

        new Git().pruneWorktrees(project(directory)).join();

        assertTrue(
                new Git().checkPrunableWorktree(project(directory), worktree).join().isEmpty(),
                "pruned worktrees should no longer be reported");
    }

    @Test
    void restoresAWorktreeFromAPlainBranchName(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Path worktree = directory.resolveSibling(directory.getFileName() + "-plain");
        run(
                directory,
                WORKTREE_BRANCH_COMMAND
                        + PlatformCommands.shellQuote(worktree.toString())
                        + FEATURE_ARGUMENT);
        run(
                directory,
                "git worktree remove --force " + PlatformCommands.shellQuote(worktree.toString()));

        new Git()
                .restoreWorktree(
                        project(directory), new Git.Worktree(worktree, FEATURE_BRANCH, true))
                .join();

        assertEquals(
                "feature\n",
                readCommand(worktree, SHOW_BRANCH_COMMAND),
                "plain branch names should be restored");
    }

    private static Project project(final Path directory) {
        return new Project("Test", directory.toString(), null);
    }

    private static Session session(final Path worktree) {
        return new Session(null, SESSION_NAME, null, null, worktree.toString());
    }

    private static String readCommand(final Path directory, final String command)
            throws IOException, InterruptedException {
        return output(directory, command);
    }

    private static void assertCompletionFailure(final java.util.function.Supplier<?> operation) {
        assertThrows(
                CompletionException.class,
                operation::get,
                "operation should complete exceptionally");
    }
}
