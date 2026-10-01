package com.jagent.desktop.services.git;

import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.models.git.Worktree;
import com.jagent.desktop.models.git.WorktreeStatusSummary;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity"})
/**
 * High-level Git service for repository and worktree operations.
 *
 * <p>Use this class when application code needs to inspect or mutate repository state (branches,
 * status, diffs, worktrees) without constructing raw command lines. It combines JGit for
 * repository-aware reads with {@link GitNative} for operations that are more reliable via the git
 * CLI.
 *
 * <p>Typical usage is:
 *
 * <pre>{@code
 * try (GitRepository repository = GitRepository.open(worktreePath)) {
 *     List<Branch> branches = repository.listAvailableBranches();
 *     repository.addWorktree(newWorktreePath, branches.getFirst());
 * }
 * }</pre>
 *
 * <p>Create and close instances with try-with-resources. Callers should handle {@link IOException}
 * as the unified failure type for both JGit and native git execution failures.
 */
public final class GitRepository implements AutoCloseable {

    private static final String FETCH = "fetch";
    private static final String ORIGIN = "origin";
    private static final String ORIGIN_REMOTE_REF_PREFIX = "refs/remotes/origin/";

    private final Repository repository;
    private final Path repositoryPath;

    private GitRepository(final Repository repository) {
        this.repository = repository;
        this.repositoryPath = this.repository.getWorkTree().toPath();
    }

    public static Path cloneRepository(final String remote, final Path destination)
            throws IOException {
        final Path normalized = destination.toAbsolutePath().normalize();
        final Path parent =
                Optional.ofNullable(normalized.getParent())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "The clone destination must have a parent directory."));
        try {
            final URIish uri = new URIish(remote);
            final String humanishName = uri.getHumanishName();
            final File targetDir = new File(parent.toFile(), humanishName);
            Git.cloneRepository().setDirectory(targetDir).setURI(remote).call().close();
            return Path.of(targetDir.getAbsolutePath());
        } catch (URISyntaxException | GitAPIException e) {
            throw new IOException(e);
        }
    }

    private void checkIsWorktreeDeletable(final Path worktree) throws IOException {
        if (worktree.equals(this.repositoryPath)) {
            throw new IllegalArgumentException("Refusing to remove main worktree: " + worktree);
        }
        if (!Files.isDirectory(worktree)) {
            throw new IllegalArgumentException(
                    "The worktree directory does not exist: " + worktree);
        }
        if (Files.isSymbolicLink(worktree)) {
            throw new IllegalArgumentException(
                    "The path is a symbolic link, not a valid worktree: " + worktree);
        }
        final var worktrees = GitNative.listWorktrees(this.repositoryPath);
        if (!worktrees.stream().anyMatch(wt -> wt.path().equals(worktree))) {
            throw new IllegalArgumentException(
                    "The path is not a registered worktree: " + worktree);
        }
    }

    public String getFileDiff(
            @NotNull final Path file,
            @NotNull final boolean staged,
            @Nullable final String sourceRef)
            throws IOException {
        try (Git git = Git.wrap(this.repository)) {
            final Path filePath = file.toAbsolutePath().normalize();
            final String unixPath =
                    this.repositoryPath
                            .toAbsolutePath()
                            .normalize()
                            .relativize(filePath)
                            .toString()
                            .replace(File.separatorChar, '/');
            final var diffCommand =
                    git.diff().setPathFilter(PathFilter.create(unixPath)).setCached(staged);
            if (sourceRef != null && !sourceRef.isBlank()) {
                final ObjectId treeId = this.repository.resolve(sourceRef + "^{tree}");
                if (treeId == null) {
                    throw new IOException("Unknown Git reference: " + sourceRef);
                }
                final CanonicalTreeParser tree = new CanonicalTreeParser();
                try (var reader = this.repository.newObjectReader()) {
                    tree.reset(reader, treeId);
                }
                diffCommand.setOldTree(tree);
            }
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            diffCommand.setOutputStream(output).call();
            return output.toString(StandardCharsets.UTF_8);
        } catch (NoWorkTreeException | GitAPIException e) {
            throw new IOException(e);
        }
    }

    public static boolean isRepository(final Path path) {
        try (var r = openRepo(path)) {
            return r.getBranch() != null;
        } catch (IOException exception) {
            return false;
        }
    }

    public static GitRepository open(final Path path) throws IOException {
        return new GitRepository(openRepo(path));
    }

    private static Repository openRepo(final Path path) throws IOException {
        return new FileRepositoryBuilder()
                .findGitDir(path.toFile())
                .readEnvironment()
                .findGitDir()
                .build();
    }

    public void addWorktree(final Path worktreePath, final Branch branch) throws IOException {
        if (branch.remote()) {
            this.fetchRemoteRef(branch.name());
        }
        GitNative.createWorktree(this.repositoryPath, worktreePath, branch);
    }

    @Override
    public void close() throws IOException {
        repository.close();
    }

    public boolean branchExists(@NotNull final String branch) throws IOException {
        return this.listLocalBranches().stream().anyMatch(b -> b.name().equals(branch));
    }

    public Branch createBranch(final Branch sourceBranch, final String newBranchName)
            throws IOException {
        try (Git git = Git.wrap(this.repository)) {
            git.branchCreate().setStartPoint(sourceBranch.name()).setName(newBranchName).call();
            return new Branch(newBranchName);
        } catch (NoWorkTreeException | GitAPIException e) {
            throw new IOException(e);
        }
    }

    public String currentBranch() throws IOException {
        return this.repository.getBranch();
    }

    /**
     * Delete the worktree at the specified path. This should be called from the project/main
     * repository
     *
     * @param worktreePath the path to the worktree to delete
     * @throws IOException
     */
    public void deleteWorktree(final Path worktreePath) throws IOException {
        this.checkIsWorktreeDeletable(worktreePath);
        GitNative.deleteWorktree(this.repositoryPath, worktreePath);
    }

    public void fetchRemote() throws IOException {
        GitNative.run(this.repositoryPath, FETCH, ORIGIN);
    }

    public void updateCurrentBranch() throws IOException {
        GitNative.run(this.repositoryPath, "pull", "--rebase");
    }

    public void updatePrimaryBranch() throws IOException {
        final String primaryBranch = primaryBranchName();
        final String currentBranch = this.currentBranch();
        if (!primaryBranch.equals(currentBranch)) {
            throw new IOException(
                    "Switch to '" + primaryBranch + "' before updating the primary branch.");
        }
        final String remoteRef = ORIGIN_REMOTE_REF_PREFIX + primaryBranch;
        try (Git git = Git.wrap(this.repository)) {
            final Status status = git.status().call();
            if (!status.isClean()) {
                throw new IOException(
                        "Commit or stash local changes before updating the primary branch.");
            }
        } catch (NoWorkTreeException | GitAPIException exception) {
            throw new IOException(exception);
        }
        GitNative.run(this.repositoryPath, FETCH, ORIGIN);
        GitNative.run(this.repositoryPath, "rebase", remoteRef);
    }

    public void mergePrimaryBranch() throws IOException {
        final String primaryBranch = primaryBranchName();
        final String remoteRef = ORIGIN_REMOTE_REF_PREFIX + primaryBranch;
        GitNative.run(this.repositoryPath, FETCH, ORIGIN);
        GitNative.run(this.repositoryPath, "merge", remoteRef);
    }

    public void rebasePrimaryBranch() throws IOException {
        final String primaryBranch = primaryBranchName();
        final String remoteRef = ORIGIN_REMOTE_REF_PREFIX + primaryBranch;
        GitNative.run(this.repositoryPath, FETCH, ORIGIN);
        GitNative.run(this.repositoryPath, "rebase", remoteRef);
    }

    public void fetchRemoteRef(final String ref) throws IOException {
        if (!Optional.ofNullable(ref)
                .filter(value -> !value.isBlank() && value.contains("/"))
                .isPresent()) {
            throw new IOException("Invalid ref: " + ref);
        }
        final int separator = ref.indexOf('/');
        final String remote = ref.substring(0, separator);
        final String branch = ref.substring(separator + 1);

        GitNative.run(this.repositoryPath, FETCH, remote, branch);
    }

    public boolean isPruneableWorktree() throws IOException {
        final var path = this.repository.getWorkTree().toPath();
        return GitNative.listWorktrees(path).stream()
                .anyMatch(w -> w.path().equals(path) && w.prunable());
    }

    public List<Branch> listAvailableBranches() throws IOException {
        final var occupied =
                this.listWorktrees().stream()
                        .map(Worktree::branch)
                        .filter(branch -> branch != null)
                        .map(Branch::new)
                        .map(Branch::withoutHeadsPrefix)
                        .collect(Collectors.toSet());

        return this.listBranches().stream()
                .filter(branch -> !occupied.contains(branch.withoutHeadsPrefix()))
                .toList();
    }

    public List<Branch> listBranches() throws IOException {
        try (Git git = Git.wrap(this.repository)) {
            final List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
            return branches.stream()
                    .map(
                            ref -> {
                                final String name = ref.getName();
                                return new Branch(name);
                            })
                    .toList();
        } catch (NoWorkTreeException | GitAPIException e) {
            throw new IOException(e);
        }
    }

    public List<Branch> listLocalBranches() throws IOException {
        try (Git git = Git.wrap(this.repository)) {
            final List<Ref> branches = git.branchList().call();
            return branches.stream()
                    .map(
                            ref -> {
                                final String name = ref.getName();
                                return new Branch(name);
                            })
                    .toList();
        } catch (NoWorkTreeException | GitAPIException e) {
            throw new IOException(e);
        }
    }

    public List<Worktree> listWorktrees() throws IOException {
        return GitNative.listWorktrees(this.repositoryPath);
    }

    public void pruneWorktrees(final Path worktreePath) throws IOException {
        GitNative.pruneWorktrees(worktreePath);
    }

    public String repositoryOrgAndName() throws IOException {
        try {
            final String remoteUrl =
                    this.repository.getConfig().getString("remote", "origin", "url");
            final URIish remoteUri = new URIish(remoteUrl);
            String path = remoteUri.getPath();
            if (!remoteUri.isRemote() || path == null || path.isBlank()) {
                throw new IOException(
                        "The remote URL: " + remoteUrl + " is not a valid remote repository.");
            }

            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            final String[] segments = path.split("/");
            if (segments.length < 2) {
                throw new IOException(
                        "The remote URL: "
                                + remoteUrl
                                + " does not contain a valid organization and repository name.");
            }
            return path;
        } catch (NoWorkTreeException | URISyntaxException e) {
            throw new IOException(e);
        }
    }

    public void restoreWorktree(@NotNull final Worktree worktree) throws IOException {
        if (worktree.branch() == null || worktree.branch().isBlank()) {
            throw new IOException("The stale worktree has no recorded branch to restore.");
        }
        GitNative.restoreWorktree(this.repositoryPath, worktree);
    }

    public WorktreeStatusSummary statusSummary() throws IOException {
        try (Git git = Git.wrap(this.repository)) {
            final Status status = git.status().call();
            final int additions = status.getAdded().size() + status.getUntracked().size();
            final int modifications = status.getChanged().size() + status.getModified().size();
            final int deletions = status.getRemoved().size() + status.getMissing().size();
            return new WorktreeStatusSummary(additions, modifications, deletions);
        } catch (NoWorkTreeException | GitAPIException e) {
            throw new IOException(e);
        }
    }

    public String diffSummary() throws IOException {
        try (Git git = Git.wrap(this.repository);
                DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(this.repository);
            final StringBuilder summary = new StringBuilder();
            final List<DiffEntry> entries = git.diff().call();
            for (final DiffEntry entry : entries) {
                final String path =
                        entry.getChangeType() == DiffEntry.ChangeType.DELETE
                                ? entry.getOldPath()
                                : entry.getNewPath();
                int additions = 0;
                int deletions = 0;
                for (final Edit edit : formatter.toFileHeader(entry).toEditList()) {
                    additions += edit.getEndB() - edit.getBeginB();
                    deletions += edit.getEndA() - edit.getBeginA();
                }
                summary.append(additions)
                        .append('\t')
                        .append(deletions)
                        .append('\t')
                        .append(path)
                        .append(System.lineSeparator());
            }
            return summary.toString();
        } catch (NoWorkTreeException | GitAPIException exception) {
            throw new IOException(exception);
        }
    }

    public WorkspaceStatus workspaceStatus(
            final boolean includeSourceBranch, @Nullable final String sourceRef)
            throws IOException {
        try (Git git = Git.wrap(this.repository)) {
            final Status status = git.status().call();
            final Map<String, String> files = new LinkedHashMap<>();
            appendStatuses(files, status.getAdded(), "A ");
            appendStatuses(files, status.getChanged(), " M");
            appendStatuses(files, status.getModified(), " M");
            appendStatuses(files, status.getRemoved(), " D");
            appendStatuses(files, status.getMissing(), " D");
            appendStatuses(files, status.getUntracked(), "??");
            appendStatuses(files, status.getConflicting(), "UU");
            final Set<String> ignoredPaths =
                    status.getIgnoredNotInIndex().stream()
                            .map(this::normalizePath)
                            .collect(Collectors.toUnmodifiableSet());
            if (includeSourceBranch && sourceRef != null && !sourceRef.isBlank()) {
                appendSourceDiff(git, files, sourceRef);
            }
            return new WorkspaceStatus(summary(files), Map.copyOf(files), ignoredPaths);
        } catch (NoWorkTreeException | GitAPIException exception) {
            throw new IOException(exception);
        }
    }

    private void appendStatuses(
            final Map<String, String> files, final Set<String> paths, final String code) {
        for (final String path : paths) {
            files.put(path, code);
        }
    }

    private String normalizePath(final String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void appendSourceDiff(
            final Git git, final Map<String, String> files, final String sourceRef)
            throws IOException, GitAPIException {
        final ObjectId sourceTreeId = this.repository.resolve(sourceRef + "^{tree}");
        if (sourceTreeId == null) {
            throw new IOException("Unknown Git reference: " + sourceRef);
        }
        final ObjectId headTreeId = this.repository.resolve("HEAD^{tree}");
        if (headTreeId == null) {
            throw new IOException("Could not resolve HEAD tree.");
        }
        final List<DiffEntry> entries =
                git.diff()
                        .setOldTree(treeParser(sourceTreeId))
                        .setNewTree(treeParser(headTreeId))
                        .setShowNameAndStatusOnly(true)
                        .call();
        for (final DiffEntry entry : entries) {
            final String path =
                    entry.getChangeType() == DiffEntry.ChangeType.DELETE
                            ? entry.getOldPath()
                            : entry.getNewPath();
            final String code =
                    switch (entry.getChangeType()) {
                        case ADD -> "A ";
                        case DELETE -> " D";
                        case MODIFY -> " M";
                        case RENAME -> "R ";
                        case COPY -> "C ";
                    };
            files.putIfAbsent(path, code);
        }
    }

    private CanonicalTreeParser treeParser(final ObjectId treeId) throws IOException {
        final CanonicalTreeParser tree = new CanonicalTreeParser();
        try (var reader = this.repository.newObjectReader()) {
            tree.reset(reader, treeId);
        }
        return tree;
    }

    private WorktreeStatusSummary summary(final Map<String, String> files) {
        int additions = 0;
        int modifications = 0;
        int deletions = 0;
        for (final String code : files.values()) {
            if (code.contains("A") || code.contains("?")) {
                additions++;
            } else if (code.contains("D")) {
                deletions++;
            } else {
                modifications++;
            }
        }
        return new WorktreeStatusSummary(additions, modifications, deletions);
    }

    private String primaryBranchName() throws IOException {
        final Ref originHead = this.repository.exactRef("refs/remotes/origin/HEAD");
        if (originHead == null) {
            throw new IOException("No Git remote HEAD is configured for origin.");
        }
        final Ref target = originHead.getTarget();
        if (target == null
                || target.getName() == null
                || !target.getName().startsWith(ORIGIN_REMOTE_REF_PREFIX)) {
            throw new IOException("Could not determine the primary branch from origin/HEAD.");
        }
        return target.getName().substring(ORIGIN_REMOTE_REF_PREFIX.length());
    }

    public record WorkspaceStatus(
            WorktreeStatusSummary summary, Map<String, String> files, Set<String> ignoredPaths) {}
}
