package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.models.PullRequestCheck;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitHubChecksMergeTest {
    @Test
    void mergeChecksPrefersRunEntriesWhenNamesOverlap() throws MalformedURLException {
        final PullRequestCheck run =
                new PullRequestCheck(
                        "build",
                        PullRequestCheck.Status.COMPLETED,
                        new Date(2_000),
                        PullRequestCheck.Conclusion.FAILURE,
                        new URL("https://example.test/run"));
        final PullRequestCheck status =
                new PullRequestCheck(
                        "build",
                        PullRequestCheck.Status.COMPLETED,
                        new Date(3_000),
                        PullRequestCheck.Conclusion.SUCCESS,
                        new URL("https://example.test/status"));

        final List<PullRequestCheck> merged = GitHub.mergeChecks(List.of(run), List.of(status));

        assertEquals(1, merged.size(), "duplicate names should collapse to one entry");
        assertEquals(
                PullRequestCheck.Conclusion.FAILURE,
                merged.getFirst().conclusion(),
                "check-run result should take precedence when names overlap");
    }

    @Test
    void mergeChecksChoosesLatestStatusPerNameAndHandlesNullCompletionDate()
            throws MalformedURLException {
        final PullRequestCheck runWithNullTimestamp =
                new PullRequestCheck(
                        "tests",
                        PullRequestCheck.Status.IN_PROGRESS,
                        null,
                        PullRequestCheck.Conclusion.UNKNOWN,
                        null);
        final PullRequestCheck oldStatus =
                new PullRequestCheck(
                        "lint",
                        PullRequestCheck.Status.COMPLETED,
                        new Date(1_000),
                        PullRequestCheck.Conclusion.FAILURE,
                        new URL("https://example.test/lint-old"));
        final PullRequestCheck newStatus =
                new PullRequestCheck(
                        "lint",
                        PullRequestCheck.Status.COMPLETED,
                        new Date(2_000),
                        PullRequestCheck.Conclusion.SUCCESS,
                        new URL("https://example.test/lint-new"));

        final List<PullRequestCheck> merged =
                GitHub.mergeChecks(List.of(runWithNullTimestamp), List.of(oldStatus, newStatus));

        assertEquals(2, merged.size(), "distinct names should remain in merged output");
        assertEquals("tests", merged.get(0).name(), "run entries should keep leading position");
        assertEquals(
                PullRequestCheck.Conclusion.SUCCESS,
                merged.get(1).conclusion(),
                "latest status by completion date should be retained");
    }
}
