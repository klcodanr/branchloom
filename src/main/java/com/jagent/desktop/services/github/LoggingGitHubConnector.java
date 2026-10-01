package com.jagent.desktop.services.github;

import com.jagent.desktop.ui.components.UiText;
import java.io.IOException;
import org.kohsuke.github.connector.GitHubConnector;
import org.kohsuke.github.connector.GitHubConnectorRequest;
import org.kohsuke.github.connector.GitHubConnectorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingGitHubConnector implements GitHubConnector {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingGitHubConnector.class);
    private final GitHubConnector delegate;

    public LoggingGitHubConnector(final GitHubConnector delegate) {
        this.delegate = delegate;
    }

    @Override
    public GitHubConnectorResponse send(final GitHubConnectorRequest request) throws IOException {
        final long started = System.nanoTime();
        try {
            final GitHubConnectorResponse response = delegate.send(request);
            LOG.debug(
                    "GitHub API {} {} -> {} ({}ms, remaining={}, reset={})",
                    request.method(),
                    request.url().toExternalForm(),
                    response.statusCode(),
                    (System.nanoTime() - started) / 1_000_000,
                    UiText.valueOrDefault(response.header("X-RateLimit-Remaining"), "unknown"),
                    UiText.valueOrDefault(response.header("X-RateLimit-Reset"), "unknown"));
            return response;
        } catch (IOException exception) {
            LOG.debug(
                    "GitHub API {} {} failed after {}ms",
                    request.method(),
                    request.url(),
                    (System.nanoTime() - started) / 1_000_000,
                    exception);
            throw exception;
        }
    }
}
