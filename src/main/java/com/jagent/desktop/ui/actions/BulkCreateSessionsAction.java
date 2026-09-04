package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.dialogs.BulkSessionDialog;
import com.jagent.desktop.ui.utils.GitUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class BulkCreateSessionsAction extends BaseAction {
    private final Git git = new Git();

    public BulkCreateSessionsAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "bulk-new-sessions";
    }

    @Override
    public String label() {
        return "Start sessions from GitHub issues";
    }

    @Override
    public boolean enabled() {
        return actionContext.appState().currentProjectId() != null;
    }

    @Override
    public void execute() {
        final AppState state = actionContext.appState();
        final ProjectId projectId = state.currentProjectId();
        if (projectId == null || state.projects().get(projectId) == null) {
            return;
        }
        if (state.appSettings().agents().isEmpty()) {
            showError("Configure an agent in Settings before starting a session.");
            return;
        }
        final Project project = state.projects().get(projectId);
        BackgroundTasks.submit(
                        "GitHub",
                        "issues",
                        () -> {
                            try {
                                return GitHub.loadIssuesForProject(project);
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new CompletionException(exception);
                            }
                        })
                .whenCompleteAsync(
                        (issues, failure) -> {
                            if (failure != null) {
                                showError(message(failure, "Could not load GitHub issues."));
                            } else if (issues.isEmpty()) {
                                JOptionPane.showMessageDialog(
                                        actionContext.window(),
                                        "No open GitHub issues were found.",
                                        "Bulk agent sessions",
                                        JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                new BulkSessionDialog(
                                                actionContext,
                                                issues,
                                                request -> create(projectId, project, request))
                                        .setVisible(true);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private void create(
            final ProjectId projectId,
            final Project project,
            final BulkSessionDialog.Request request) {
        final Handle job = actionContext.viewCoordinator().backgroundJobs().start("Bulk sessions");
        BackgroundTasks.submit(
                        "Sessions",
                        "bulk-create",
                        () -> createAll(projectId, project, request, job))
                .whenCompleteAsync(
                        (result, failure) -> {
                            if (failure != null) {
                                job.fail(message(failure, "Bulk session creation failed."));
                            } else {
                                job.complete();
                                showResult(result);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private Result createAll(
            final ProjectId projectId,
            final Project project,
            final BulkSessionDialog.Request request,
            final Handle job) {
        final Set<String> names = existingNames(projectId);
        final List<String> successes = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final AtomicInteger completed = new AtomicInteger();
        for (final GitHub.Issue issue : request.issues()) {
            final String name = uniqueName(issue, names);
            try {
                job.update(
                        "Creating "
                                + completed.incrementAndGet()
                                + " of "
                                + request.issues().size()
                                + ": "
                                + name);
                createOne(projectId, project, issue, request.agent(), name);
                names.add(name.toLowerCase(Locale.ROOT));
                successes.add("#" + issue.number() + " " + name);
            } catch (RuntimeException | IOException exception) {
                failures.add("#" + issue.number() + ": " + message(exception, "Creation failed."));
            }
        }
        return new Result(successes, failures);
    }

    private void createOne(
            final ProjectId projectId,
            final Project project,
            final GitHub.Issue issue,
            final Agent agent,
            final String name)
            throws IOException {
        final String prompt = issuePrompt(issue);
        final Session draft = new Session(projectId, name, agent.name, prompt, "");
        final String branch = GitUtils.toBranchSlug(name);
        if (git.branchExists(project, branch).join()) {
            throw new IOException("Branch already exists: " + branch);
        }
        final AppState state = actionContext.appState();
        final String worktreePath =
                Template.resolvePath(
                        Template.expand(
                                Template.worktree(project, state.appSettings()),
                                project,
                                draft,
                                false),
                        project);
        final Path path = Path.of(worktreePath);
        if (GitUtils.isWorktreeRegistered(state.sessions(), path) || Files.exists(path)) {
            throw new IOException("Worktree path is already in use: " + worktreePath);
        }
        git.createWorktree(project, branch, path).join();
        SwingUtilities.invokeLater(
                () -> finishSession(projectId, project, agent, name, prompt, worktreePath));
    }

    private void finishSession(
            final ProjectId projectId,
            final Project project,
            final Agent agent,
            final String name,
            final String prompt,
            final String worktreePath) {
        final AppState state = actionContext.appState();
        final Session session = new Session(projectId, name, agent.name, prompt, worktreePath);
        try {
            AgentContext.write(project, session);
            final SessionId sessionId = state.addSession(projectId, session);
            final var terminalId =
                    state.addTerminal(
                            sessionId,
                            new Terminal(
                                    sessionId,
                                    agent.name,
                                    agent.newSessionCommand.replace(
                                            "{prompt}", PlatformCommands.shellQuote(prompt))));
            final Terminal terminal = state.terminals().get(terminalId);
            final TerminalPanel terminalPanel =
                    TerminalPanel.retained(
                            terminalId,
                            terminal,
                            Path.of(worktreePath).toAbsolutePath().normalize(),
                            project.name() + " > " + name + " > " + terminal.title());
            if (project.startupCommands().isEmpty()) {
                terminalPanel.start();
            } else {
                final Handle setup =
                        actionContext.viewCoordinator().backgroundJobs().start("Session setup");
                runStartupCommand(project, worktreePath, setup, 0, terminalPanel::start);
            }
            actionContext
                    .viewCoordinator()
                    .updateView(
                            ViewId.SESSION,
                            ViewState.sessionTerminal(projectId, sessionId, terminalId));
        } catch (IOException exception) {
            showError(message(exception, "Could not save the new session."));
        }
    }

    private void runStartupCommand(
            final Project project,
            final String worktreePath,
            final Handle job,
            final int index,
            final Runnable onComplete) {
        if (index >= project.startupCommands().size()) {
            job.complete();
            onComplete.run();
            return;
        }
        job.update(
                "Running startup command "
                        + (index + 1)
                        + " of "
                        + project.startupCommands().size());
        CommandRunner.run(
                project.startupCommands().get(index),
                Path.of(worktreePath),
                ignored -> {},
                () -> runStartupCommand(project, worktreePath, job, index + 1, onComplete),
                output ->
                        job.fail(
                                output == null || output.isBlank()
                                        ? "Setup command failed."
                                        : output));
    }

    private Set<String> existingNames(final ProjectId projectId) {
        final AppState state = actionContext.appState();
        final Project project = state.projects().get(projectId);
        final Set<String> names = new HashSet<>();
        project.sessionIds().stream()
                .map(state.sessions()::get)
                .filter(session -> session != null)
                .map(session -> session.name().toLowerCase(Locale.ROOT))
                .forEach(names::add);
        return names;
    }

    private String uniqueName(final GitHub.Issue issue, final Set<String> names) {
        final String base = "issue-" + issue.number() + "-" + GitUtils.toBranchSlug(issue.title());
        String name = base;
        int suffix = 2;
        while (names.contains(name.toLowerCase(Locale.ROOT))) {
            name = base + "-" + suffix++;
        }
        return name;
    }

    private String issuePrompt(final GitHub.Issue issue) {
        return "Work on GitHub issue #"
                + issue.number()
                + ": "
                + issue.title()
                + "\n\n"
                + issue.body()
                + "\n\nIssue: "
                + issue.url();
    }

    private void showResult(final Result result) {
        final StringBuilder text = new StringBuilder(64);
        text.append("Created ").append(result.successes().size()).append(" session(s).");
        if (!result.failures().isEmpty()) {
            text.append("\n\nFailed:\n").append(String.join("\n", result.failures()));
        }
        JOptionPane.showMessageDialog(
                actionContext.window(),
                text,
                "Bulk agent sessions",
                result.failures().isEmpty()
                        ? JOptionPane.INFORMATION_MESSAGE
                        : JOptionPane.WARNING_MESSAGE);
    }

    private void showError(final String message) {
        JOptionPane.showMessageDialog(
                actionContext.window(), message, "Bulk agent sessions", JOptionPane.ERROR_MESSAGE);
    }

    private String message(final Throwable failure, final String fallback) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? fallback
                : cause.getMessage();
    }

    private record Result(List<String> successes, List<String> failures) {}
}
