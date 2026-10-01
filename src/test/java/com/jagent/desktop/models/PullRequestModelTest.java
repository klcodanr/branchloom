package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import org.junit.jupiter.api.Test;

class PullRequestModelTest {
    @Test
    void abbreviatedHeadShaHandlesBlankShortAndLongValues() throws MalformedURLException {
        assertEquals(
                "missing", request("").abbreviatedHeadSha(), "blank SHA should render as missing");
        assertEquals(
                "abc123",
                request("abc123").abbreviatedHeadSha(),
                "short SHA should be shown as-is");
        assertEquals(
                "1234567...bcdef",
                request("1234567890abcdef").abbreviatedHeadSha(),
                "long SHA should be abbreviated");
    }

    private static PullRequest request(final String sha) throws MalformedURLException {
        final Project project = new Project("Demo", "/tmp/demo", null);
        return new PullRequest(
                ProjectId.create(),
                project,
                7,
                PullRequest.State.OPEN,
                "Title",
                "Body",
                new URL("https://example.test/pr/7"),
                new Date(),
                new Date(),
                new GitHubUser("author", new URL("https://example.test/author")),
                "feature/test",
                sha,
                "main");
    }
}
