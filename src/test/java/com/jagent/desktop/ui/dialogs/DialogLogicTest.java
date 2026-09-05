package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.GitHub.Issue;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DialogLogicTest {
    private static final Agent AGENT = new Agent("Agent", "agent");

    @Test
    void bulkSessionRequestsRequireIssuesAndCopySelections() {
        final Issue issue = new Issue(7, "Fix bug", "Details", "https://example.test/7");

        assertFalse(BulkSessionDialog.hasSelection(List.of()), "empty issue selection is invalid");
        assertTrue(BulkSessionDialog.hasSelection(List.of(issue)), "an issue selection is valid");
        assertEquals(
                List.of(issue),
                BulkSessionDialog.request(List.of(issue), AGENT).issues(),
                "bulk request should retain selected issues");
    }

    @Test
    void pastedSessionLinesAreTrimmedAndBlankLinesRemoved() {
        assertEquals(
                List.of("first", "second"),
                PasteSessionsDialog.nonBlankLines(" first \n\n second\n"),
                "pasted session names should be normalized");
        assertEquals(
                "{prompt}",
                PasteSessionsDialog.request("first", AGENT, " {prompt} ").basePrompt(),
                "the base prompt should be trimmed");
    }

    @Test
    void reviewPromptSubstitutesRequestDetailsAndRejectsBlankPrompts() {
        final PullRequest request =
                new PullRequest(
                        ProjectId.create(),
                        12,
                        "Improve tests",
                        "description",
                        "comments",
                        "https://example.test/12",
                        "created",
                        "updated",
                        "APPROVED",
                        "MERGEABLE",
                        false,
                        "author",
                        "branch",
                        1,
                        1,
                        "PASSING");

        assertEquals(
                "Review #12: Improve tests",
                ReviewDialog.defaultPrompt("Review #{number}: {title}", request),
                "review prompt placeholders should be replaced");
        assertFalse(ReviewDialog.validPrompt(" \n "), "blank review prompts should be rejected");
        assertTrue(ReviewDialog.validPrompt("Review it"), "non-blank review prompts are valid");
    }

    @Test
    void progressOperationRunsHeadlesslyAndReportsSuccess() throws InterruptedException {
        final CountDownLatch completed = new CountDownLatch(1);

        ProgressOperation.run(
                null,
                "Test operation",
                "Working",
                () -> null,
                completed::countDown,
                failure -> completed.countDown());

        assertTrue(
                completed.await(5, TimeUnit.SECONDS),
                "headless progress operation should report completion");
    }
}
