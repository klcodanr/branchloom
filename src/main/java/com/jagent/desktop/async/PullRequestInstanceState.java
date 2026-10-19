package com.jagent.desktop.async;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.github.GitHub;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullRequestInstanceState
        extends AsyncLoadingState<
                PullRequestInstanceState.PullRequestKey,
                PullRequestInstanceState.PullRequestKey,
                List<PullRequestDetails>,
                AsyncStateListener<List<PullRequestDetails>>> {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestInstanceState.class);
    private final AppState appState;

    public record PullRequestKey(ProjectId projectId, String filter) {}

    public PullRequestInstanceState(final AppState appState, final BackgroundJobs backgroundJobs) {
        super("Pull Requests", backgroundJobs);
        this.appState = appState;
    }

    @Override
    protected List<PullRequestDetails> load(
            final PullRequestKey instance, final PullRequestKey ignored, final Handle jobHandle)
            throws IOException {
        final GitHub gitHub = GitHub.forProject(appState, instance.projectId());

        final List<PullRequest> pullRequests = gitHub.listPullRequests(instance.filter());

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            final List<CompletableFuture<PullRequestDetails>> futures =
                    pullRequests.stream()
                            .map(
                                    pr ->
                                            CompletableFuture.supplyAsync(
                                                    () -> {
                                                        try {
                                                            return gitHub.getPullRequestDetails(pr);
                                                        } catch (IOException
                                                                | InterruptedException e) {
                                                            LOG.warn(
                                                                    "Failed to get pull request details for {}",
                                                                    pr,
                                                                    e);
                                                        }
                                                        return null;
                                                    },
                                                    executor))
                            .toList();
            return futures.stream().map(CompletableFuture::join).filter(pr -> pr != null).toList();
        }
    }
}
