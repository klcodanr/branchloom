package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.GitHub.Issue;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowLogicTest {
    @Test
    void bulkIssueCandidatesContainBranchNamesLabelsAndPrompts() {
        final Issue issue = new Issue(42, "Fix Login Flow", "Use OAuth", "https://example.test/42");

        final var candidate = BulkCreateSessionsAction.candidates(List.of(issue)).get(0);

        assertEquals(
                "issue-42-fix-login-flow", candidate.name(), "candidate name should be slugged");
        assertEquals("#42", candidate.label(), "candidate label should identify the issue");
        assertTrue(
                candidate.prompt().contains("Use OAuth"),
                "candidate prompt should include the body");
        assertTrue(
                candidate.prompt().contains("https://example.test/42"),
                "candidate prompt should include the issue URL");
    }

    @Test
    void candidatesListIsEmptyForNoIssues() {
        assertTrue(
                BulkCreateSessionsAction.candidates(List.of()).isEmpty(),
                "no issues should produce no candidates");
    }
}
