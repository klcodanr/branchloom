package com.jagent.desktop.async;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.SessionSummaryInfo;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.git.GitRepository.WorktreeStatus;
import com.jagent.desktop.services.github.GitHub;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionInstanceState
        extends AsyncLoadingState<
                SessionId, Session, SessionSummaryInfo, AsyncStateListener<SessionSummaryInfo>> {
    private static final Logger LOG = LoggerFactory.getLogger(SessionInstanceState.class);
    private final AppState appState;

    public SessionInstanceState(final AppState appState, final BackgroundJobs backgroundJobs) {
        super("Sessions", backgroundJobs);
        this.appState = appState;
    }

    @Override
    protected SessionSummaryInfo load(
            final SessionId instance, final Session session, final Handle jobHandle) {
        final GitHub gitHub = GitHub.forProject(appState, appState.currentProjectId());
        final Project project = appState.currentProject();

        final CompletableFuture<PullRequestDetails> pullRequestDetails =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                final String worktreePath = session.worktreePath();
                                if (worktreePath == null || worktreePath.isBlank()) {
                                    return null;
                                }
                                final PullRequest request =
                                        gitHub.getPullRequest(Path.of(worktreePath));

                                return gitHub.getPullRequestDetails(request);
                            } catch (IOException | InterruptedException e) {
                                LOG.warn("Failed to load pull request", e);
                                return null;
                            }
                        });

        final CompletableFuture<String> agentContext =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                final String globalContextPath =
                                        this.appState.appSettings().agentContextPath();
                                final Path path =
                                        AgentContext.path(project, session, globalContextPath);
                                final String githubUser =
                                        path != null && Files.exists(path)
                                                ? null
                                                : githubUser(gitHub);
                                return AgentContext.read(
                                        project, session, globalContextPath, githubUser);
                            } catch (IOException e) {
                                LOG.warn("Failed to load agent context", e);
                                return null;
                            }
                        });

        final CompletableFuture<WorktreeStatus> worktreeStatus =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                try (GitRepository repository =
                                        GitRepository.open(Path.of(session.worktreePath()))) {
                                    return repository.worktreeStatus();
                                }
                            } catch (IOException e) {
                                LOG.warn("Failed to load worktree status", e);
                                return null;
                            }
                        });

        CompletableFuture.allOf(pullRequestDetails, agentContext, worktreeStatus).join();
        return new SessionSummaryInfo(
                agentContext.join(), pullRequestDetails.join(), worktreeStatus.join());
    }

    private String githubUser(final GitHub gitHub) {
        try {
            return gitHub.getLogin();
        } catch (IOException exception) {
            return null;
        }
    }
}
