package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.github.Issue;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DialogLogicTest {
    private static final Agent AGENT = new Agent("Agent", "agent");

    @Test
    void bulkSessionRequestsRequireIssuesAndCopySelections() throws MalformedURLException {
        final Issue issue = new Issue(7, "Fix bug", "Details", new URL("https://example.test/7"));

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
    void reviewPromptsRejectBlankValues() {
        assertFalse(ReviewDialog.validPrompt(" \n "), "blank review prompts should be rejected");
        assertTrue(ReviewDialog.validPrompt("Review it"), "non-blank review prompts are valid");
    }

    @Test
    void progressOperationRunsHeadlesslyAndReportsSuccess()
            throws InterruptedException, ExecutionException {
        final CountDownLatch completed = new CountDownLatch(1);

        ProgressOperation.run("Test operation", "Working", () -> null)
                .thenRun(completed::countDown)
                .get();

        assertTrue(
                completed.await(5, TimeUnit.SECONDS),
                "headless progress operation should report completion");
    }
}
