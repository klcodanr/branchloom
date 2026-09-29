package com.jagent.desktop.services;

import com.jagent.desktop.models.GitHubConnection;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewEvent;

/** Pull-request actions backed by the GitHub REST API. */
public final class GitHubPullRequest {
    private GitHubPullRequest() {}

    public static void markReady(
            final Project project,
            final int number,
            final java.util.Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        updateDraft(project, number, false, configuredConnections);
    }

    public static void convertToDraft(
            final Project project,
            final int number,
            final java.util.Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        updateDraft(project, number, true, configuredConnections);
    }

    public static void close(
            final Project project,
            final int number,
            final java.util.Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        GitHub.nativePullRequest(project, number, configuredConnections).close();
    }

    public static void merge(
            final Project project,
            final int number,
            final java.util.Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        GitHub.nativePullRequest(project, number, configuredConnections)
                .merge(null, null, GHPullRequest.MergeMethod.SQUASH);
    }

    public static void approve(
            final Project project,
            final int number,
            final java.util.Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        final GHPullRequest request =
                GitHub.nativePullRequest(project, number, configuredConnections);
        request.createReview().event(GHPullRequestReviewEvent.APPROVE).create();
    }

    public static String baseBranch(
            final ProjectId projectId,
            final Project project,
            final Path worktree,
            final java.util.Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        return GitHub.pullRequest(projectId, project, worktree, configuredConnections).baseBranch();
    }

    private static void updateDraft(
            final Project project,
            final int number,
            final boolean draft,
            final java.util.Map<String, GitHubConnection> configuredConnections)
            throws IOException, InterruptedException {
        final URL url =
                URI.create(
                                GitHub.apiEndpoint(project, configuredConnections)
                                        + "/repos/"
                                        + GitHub.nativePullRequest(
                                                        project, number, configuredConnections)
                                                .getRepository()
                                                .getFullName()
                                        + "/pulls/"
                                        + number)
                        .toURL();
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PATCH");
        connection.setRequestProperty(
                "Authorization", "Bearer " + GitHub.token(project, configuredConnections));
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection
                .getOutputStream()
                .write(("{\"draft\":" + draft + "}").getBytes(StandardCharsets.UTF_8));
        if (connection.getResponseCode() / 100 != 2) {
            throw new IOException("GitHub returned HTTP " + connection.getResponseCode());
        }
    }
}
