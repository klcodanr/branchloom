package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.GitHub.Issue;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowLogicTest {
    private static final String FEATURE = "feature";

    @Test
    void projectChecksDetectDuplicateNamesAndNormalizedPaths() {
        final Project project = new Project("Demo", "demo", null);

        assertTrue(
                CreateProjectAction.duplicateName(List.of(project), "demo"),
                "project names should be compared case-insensitively");
        assertTrue(
                CreateProjectAction.duplicatePath(
                        List.of(project), Path.of("demo").toAbsolutePath().normalize()),
                "project paths should be normalized before comparison");
    }

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
    void bulkFailureMessageUsesDeepestCauseOrFallback() {
        assertEquals(
                "root failure",
                BulkCreateSessionsAction.message(
                        new IllegalStateException("wrapper", new RuntimeException("root failure")),
                        "fallback"),
                "the deepest failure message should be displayed");
        assertEquals(
                "fallback",
                BulkCreateSessionsAction.message(new RuntimeException(), "fallback"),
                "missing failure messages should use the fallback");
    }

    @Test
    void importBranchNamesAvoidExistingNamesAndResolveRemoteBranches() {
        final Set<String> names = Set.of(FEATURE, FEATURE + "-2");
        final var remote =
                new ImportBranchAction.BranchChoice("origin/feature", "origin/feature", true);
        final var local = new ImportBranchAction.BranchChoice(FEATURE, FEATURE, false);

        assertEquals(
                FEATURE + "-3",
                ImportBranchAction.uniqueName(FEATURE, names),
                "duplicate names should get a suffix");
        assertEquals(FEATURE, remote.localName(), "remote names should drop the remote prefix");
        assertEquals(FEATURE, local.localName(), "local names should be retained");
    }
}
