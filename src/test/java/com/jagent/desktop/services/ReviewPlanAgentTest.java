package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewPlanAgentTest {
    @Test
    void appendsQuotedPromptWhenCommandHasNoPlaceholder() {
        final String command =
                ReviewPlanAgent.command("review-agent", "prioritize security", List.of());

        assertTrue(command.startsWith("review-agent '"), "command should include a quoted prompt");
        assertTrue(command.contains("prioritize security"), "user instructions should be included");
        assertTrue(command.contains("Review requests:"), "request section should be included");
    }

    @Test
    void replacesPromptPlaceholderAndIncludesRequestDetails() {
        final PullRequest request =
                new PullRequest(
                        ProjectId.create(),
                        42,
                        "Improve escaping",
                        "description",
                        "comments",
                        "https://example.test/pull/42",
                        "created",
                        "updated",
                        "APPROVED",
                        "MERGEABLE",
                        false,
                        "alice",
                        "feature/escaping",
                        3,
                        3,
                        "PASSING");

        final String command =
                ReviewPlanAgent.command("review-agent {prompt}", "focus on risk", List.of(request));

        assertTrue(command.startsWith("review-agent '"), "placeholder should be replaced");
        assertTrue(
                command.contains("Project: " + request.projectId().value()),
                "project should be included");
        assertTrue(command.contains("Number: #42"), "number should be included");
        assertTrue(command.contains("Title: Improve escaping"), "title should be included");
        assertTrue(command.contains("URL: https://example.test/pull/42"), "URL should be included");
        assertTrue(command.contains("Draft: false"), "draft state should be included");
        assertTrue(command.contains("Checks: PASSING"), "check state should be included");
        assertTrue(command.contains("Mergeability: MERGEABLE"), "mergeability should be included");
        assertTrue(!command.endsWith(" {prompt}"), "placeholder should not remain");
    }
}
