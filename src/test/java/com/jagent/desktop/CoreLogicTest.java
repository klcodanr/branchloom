package com.jagent.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestGroup;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ReviewPlanAgent;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiText;
import com.jagent.desktop.ui.utils.GitUtils;
import java.awt.Color;
import java.io.InvalidObjectException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CoreLogicTest {
    private static final String VALUE_MESSAGE = "core value should match";
    private static final String DEMO_NAME = "Demo";
    private static final String DEMO_PATH = "/tmp/demo";
    private static final String AGENT_NAME = "agent";
    private static final String PROMPT = "prompt";
    private static final String MERGEABLE = "MERGEABLE";
    private static final String APPROVED = "APPROVED";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String TITLE = "Title";
    private static final String DESCRIPTION = "Description";
    private static final String COMMENTS = "Comments";
    private static final String CREATED = "created";
    private static final String UPDATED = "updated";
    private static final String AUTHOR = "author";
    private static final String FEATURE = "feature";
    private static final String PENDING = "PENDING";
    private static final String READY_REASON =
            "Review requested, ready to inspect, and not blocked by checks or conflicts.";
    private static final String WAITING_REASON =
            "Review requested, but the request is waiting on author, checks, or mergeability.";
    private static final String GENERAL_FOCUS = "Review the change, checks, and recent comments.";
    private static final String DRAFT_FOCUS = "Confirm whether the draft is ready for review.";
    private static final String FAILING_FOCUS =
            "Check failing CI before spending time on implementation details.";
    private static final String CONFLICT_FOCUS =
            "Confirm the conflict scope and whether a useful review is possible.";

    @Test
    void projectCanBeAssignedToAGroupWithoutChangingItsConfiguration() {
        final Project project = project("Demo", DEMO_PATH);

        final Project grouped = project.withGroup("Work");

        assertEquals("Work", grouped.group(), "assigned group should be retained");
        assertEquals(project.name(), grouped.name(), "group assignment should preserve the name");
        assertEquals(project.path(), grouped.path(), "group assignment should preserve the path");
        assertEquals(
                project.startupCommands(),
                grouped.startupCommands(),
                "group assignment should preserve project configuration");
    }

    @Test
    void branchSlugsNormalizeInputAndHaveFallbacks() {
        assertEquals("branch", GitUtils.toBranchSlug(null), "null should use fallback");
        assertEquals("branch", GitUtils.toBranchSlug("  "), "blank should use fallback");
        assertEquals(
                "cafe-au-lait", GitUtils.toBranchSlug("Café au lait"), "text should normalize");
        assertEquals("branch", GitUtils.toBranchSlug("!!!"), "punctuation should use fallback");
        assertTrue(
                GitUtils.toBranchSlug("a".repeat(120)).length() <= 100, "slug should be bounded");
    }

    @Test
    void templateExpansionHandlesDefaultsAndEscaping() {
        final Project project = project("Demo Project", DEMO_PATH);
        final Session session = new Session(null, "Feature branch", AGENT_NAME, PROMPT, null);
        final AppSettings settings = Defaults.appSettings();

        assertEquals(
                settings.worktreeTemplate(),
                Template.worktree(project, settings),
                "global worktree template should be selected");
        final Project configured =
                new Project(
                        DEMO_NAME,
                        DEMO_PATH,
                        null,
                        null,
                        null,
                        "custom/{sessionSlug}",
                        null,
                        List.of(),
                        List.of());
        assertEquals(
                "custom/{sessionSlug}",
                Template.worktree(configured, settings),
                "project template should override global template");
        assertEquals(
                "Demo Project/feature-branch/feature-branch",
                Template.expand(
                        "{projectName}/{sessionName}/{sessionSlug}", project, session, false),
                "unescaped values should preserve display names");
        assertEquals(
                "demo-project/Feature branch",
                Template.expand("{projectName}/{sessionName}", project, session, true),
                "escaped values should use branch-safe values");
        assertEquals(
                "/tmp/demo/child",
                Template.resolvePath("child", project),
                "relative path should resolve against project");
        final Path absolute = Path.of(System.getProperty("java.io.tmpdir"), "absolute");
        assertEquals(
                absolute.normalize().toString(),
                Template.resolvePath(absolute.toString(), project),
                "absolute paths should remain absolute");
    }

    @Test
    void templateExpansionReplacesPathsAndMissingWorktrees() {
        final Project project = project("Demo", "/tmp/project path");
        final Session session = new Session(null, "Feature branch", AGENT_NAME, PROMPT, null);

        assertEquals(
                "Demo|/tmp/project path|feature-branch|feature-branch||{unknown}",
                Template.expand(
                        "{projectName}|{projectPath}|{sessionName}|{sessionSlug}|{worktreePath}|{unknown}",
                        project,
                        session,
                        false),
                "unescaped template should expand all placeholders");
        assertEquals(
                "demo|'/tmp/project path'|Feature branch|feature-branch|''",
                Template.expand(
                        "{projectName}|{projectPath}|{sessionName}|{sessionSlug}|{worktreePath}",
                        project,
                        session,
                        true),
                "escaped template should quote paths");
    }

    @Test
    void pullRequestsGroupAndCompareByStableIdentity() {
        final ProjectId projectId = ProjectId.create();
        final PullRequest draft = pullRequest(projectId, true, MERGEABLE, "");
        final PullRequest changes =
                new PullRequest(
                        projectId,
                        2,
                        TITLE,
                        DESCRIPTION,
                        COMMENTS,
                        "https://example.test/1",
                        CREATED,
                        UPDATED,
                        "CHANGES_REQUESTED",
                        MERGEABLE,
                        false,
                        AUTHOR,
                        FEATURE,
                        1,
                        1,
                        "PASSING");
        final PullRequest approved = pullRequest(projectId, false, MERGEABLE, APPROVED);
        final PullRequest conflicting = pullRequest(projectId, false, "CONFLICTING", APPROVED);
        final PullRequest failingChecks =
                new PullRequest(
                        projectId,
                        3,
                        TITLE,
                        DESCRIPTION,
                        COMMENTS,
                        "https://example.test/3",
                        CREATED,
                        UPDATED,
                        UNKNOWN,
                        MERGEABLE,
                        false,
                        AUTHOR,
                        FEATURE,
                        0,
                        1,
                        "FAILING");

        assertEquals(PullRequestGroup.NOT_READY.label(), draft.relevanceGroup(), VALUE_MESSAGE);
        assertEquals(
                PullRequestGroup.WAITING_FOR_CHANGES.label(),
                changes.relevanceGroup(),
                VALUE_MESSAGE);
        assertEquals(PullRequestGroup.APPROVED.label(), approved.relevanceGroup(), VALUE_MESSAGE);
        assertEquals(
                PullRequestGroup.NOT_READY.label(), conflicting.relevanceGroup(), VALUE_MESSAGE);
        assertEquals(
                PullRequestGroup.NOT_READY.label(), failingChecks.relevanceGroup(), VALUE_MESSAGE);
        assertTrue(conflicting.hasBlockingMergeability(), "conflicting mergeability should block");
        assertTrue(failingChecks.hasBlockingChecks(), "failing checks should block");
        Assertions.assertFalse(approved.hasBlockingChecks(), "passing checks should not block");
        Assertions.assertFalse(
                approved.hasBlockingMergeability(), "mergeable state should not block");
        assertEquals(
                PullRequestGroup.READY_FOR_REVIEW.label(),
                pullRequest(projectId, false, MERGEABLE, UNKNOWN).relevanceGroup(),
                "unknown review status should be ready");
        assertEquals(
                PullRequestGroup.NOT_READY.label(),
                pullRequest(projectId, false, UNKNOWN, APPROVED).relevanceGroup(),
                "unknown mergeability should be not ready");
        assertEquals(
                PullRequestGroup.APPROVED.label(),
                pullRequest(projectId, false, "BEHIND", APPROVED).relevanceGroup(),
                "behind mergeability should not force not ready");
        assertTrue(
                pullRequest(projectId, false, "BEHIND", APPROVED).mergeActionAllowed(),
                "behind mergeability should allow merge actions");
        assertTrue(
                pullRequest(projectId, false, "BEHIND", APPROVED).readyForReviewQueue(),
                "behind mergeability with passing checks should be review-queue ready");
        assertTrue(
                pullRequest(projectId, false, "QUEUED", APPROVED).readyForReviewQueue(),
                "queued requests with passing checks should be review-queue ready");
        assertEquals(
                READY_REASON,
                pullRequest(projectId, false, "QUEUED", APPROVED).reviewQueueReason(),
                "queued requests should report the ready reason");
        assertEquals(
                GENERAL_FOCUS,
                pullRequest(projectId, false, "QUEUED", APPROVED).reviewQueueFocus(),
                "queued requests should use the general review focus");
        Assertions.assertFalse(
                pullRequest(projectId, false, UNKNOWN, APPROVED).mergeActionAllowed(),
                "unknown mergeability should block merge actions");
        assertEquals(
                WAITING_REASON,
                pullRequest(projectId, false, UNKNOWN, APPROVED).reviewQueueReason(),
                "unknown mergeability should report waiting reason");
        assertEquals(
                DRAFT_FOCUS,
                pullRequest(projectId, true, MERGEABLE, APPROVED).reviewQueueFocus(),
                "draft requests should focus on readiness");
        assertEquals(
                FAILING_FOCUS,
                pullRequestWithChecks(projectId, false, MERGEABLE, APPROVED, "FAILING")
                        .reviewQueueFocus(),
                "failing requests should focus on CI");
        assertEquals(
                CONFLICT_FOCUS,
                pullRequest(projectId, false, "CONFLICTING", APPROVED).reviewQueueFocus(),
                "conflicting requests should focus on conflict scope");
        assertEquals(
                PullRequestGroup.NOT_READY.label(),
                pullRequestWithChecks(projectId, false, MERGEABLE, APPROVED, PENDING)
                        .relevanceGroup(),
                "pending checks should be not ready");
        Assertions.assertFalse(
                pullRequestWithChecks(projectId, false, MERGEABLE, APPROVED, PENDING)
                        .readyForReviewQueue(),
                "pending checks should block review queue readiness");
        Assertions.assertFalse(
                pullRequestWithChecks(projectId, false, MERGEABLE, APPROVED, PENDING)
                        .mergeActionAllowed(),
                "pending checks should block merge actions");
        assertEquals(approved, approved, "request should equal itself");
        Assertions.assertNotEquals(approved, changes, "different request numbers should differ");
        Assertions.assertNotEquals(approved, "not a request", "different types should differ");
        assertEquals(4, PullRequestGroup.ordered().size(), "all groups should be ordered");
    }

    @Test
    void modelValueObjectsValidateAndPreserveRelationships() throws InvalidObjectException {
        assertThrows(IllegalArgumentException.class, () -> new ProjectId(null));
        assertThrows(IllegalArgumentException.class, () -> new SessionId(null));
        assertThrows(IllegalArgumentException.class, () -> new TerminalId(null));

        final Project project = project(DEMO_NAME, DEMO_PATH);
        final ProjectId sessionProjectId = ProjectId.create();
        final Session session =
                new Session(sessionProjectId, "Feature", AGENT_NAME, PROMPT, "/tmp/work");
        final Session emptySession =
                new Session(sessionProjectId, "Empty", null, null, null, null, null);
        final Session renamed = session.withName("Renamed");
        assertEquals("Renamed", renamed.name(), "session name should change");
        assertEquals(session.created(), renamed.created(), "rename should preserve creation time");
        assertTrue(emptySession.terminalIds().isEmpty(), "null terminals should default to empty");

        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId storedProjectId = state.addProject(project);
        final SessionId sessionId = state.addSession(storedProjectId, session);
        final var terminalId =
                state.addTerminal(
                        sessionId, new Terminal(sessionId, storedProjectId, "Shell", "sh"));
        assertEquals(
                List.of(terminalId), state.sessions().get(sessionId).terminalIds(), VALUE_MESSAGE);
        state.removeTerminal(terminalId);
        assertTrue(
                state.sessions().get(sessionId).terminalIds().isEmpty(),
                "terminal should be removed");

        final Agent agent = new Agent("Agent", "agent --prompt {prompt}");
        assertEquals("Agent", agent.toString(), VALUE_MESSAGE);
        assertEquals("agent --prompt {prompt}", agent.newSessionCommand, VALUE_MESSAGE);
        assertEquals("agent --prompt ", agent.openCommand, VALUE_MESSAGE);
    }

    @Test
    void relationshipHelpersCoverEmptyAndPopulatedCollections() {
        final ProjectId projectId = ProjectId.create();
        final Project project = project(DEMO_NAME, DEMO_PATH);
        final String worktree = Path.of(System.getProperty("java.io.tmpdir"), "work").toString();
        final Session session = new Session(projectId, "Feature", AGENT_NAME, PROMPT, worktree);
        final SessionId sessionId = SessionId.create();
        final TerminalId terminalId = TerminalId.create();
        final Project withSession = project.withNewSession(sessionId);
        final Project nullSessions =
                new Project(
                        "Null sessions",
                        "/tmp/null",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null);
        final Session withTerminal = session.withNewTerminal(terminalId);

        assertEquals(
                List.of(),
                project.projectSessions(
                        new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of())),
                "missing sessions should produce no entries");
        assertEquals(List.of(sessionId), withSession.sessionIds(), VALUE_MESSAGE);
        assertTrue(nullSessions.sessionIds().isEmpty(), "null sessions should default to empty");
        assertEquals(
                List.of(), withSession.withRemovedSession(sessionId).sessionIds(), VALUE_MESSAGE);
        assertEquals(List.of(terminalId), withTerminal.terminalIds(), VALUE_MESSAGE);
        assertEquals(
                List.of(),
                withTerminal.withRemovedTerminal(terminalId).terminalIds(),
                VALUE_MESSAGE);

        final AppSettings defaults = new AppSettings(null, null, null, null, null, null);
        assertTrue(defaults.agents().isEmpty(), "null agents should default to empty");
        assertTrue(defaults.groupOrder().isEmpty(), "null groups should default to empty");
        assertTrue(defaults.tools().isEmpty(), "null tools should default to empty");
        assertEquals("", defaults.agentContextPath(), "context path should default to blank");
        assertEquals(
                Defaults.DEFAULT_WORKTREE_TEMPLATE, defaults.worktreeTemplate(), VALUE_MESSAGE);
        assertEquals(
                Defaults.DEFAULT_WORKTREE_TEMPLATE,
                new AppSettings(List.of(), List.of(), "review", "Dark", List.of(), "  ")
                        .worktreeTemplate(),
                VALUE_MESSAGE);
        assertEquals(
                "global/context.md",
                new AppSettings(
                                List.of(),
                                List.of(),
                                "review",
                                "Dark",
                                List.of(),
                                "custom",
                                " global/context.md ",
                                false,
                                "",
                                Defaults.DEFAULT_REVIEW_PLAN_PROMPT)
                        .agentContextPath(),
                "context path should be trimmed");
        assertEquals(
                "custom",
                new AppSettings(List.of(), List.of(), "review", "Dark", List.of(), "custom")
                        .worktreeTemplate(),
                VALUE_MESSAGE);

        final Map<SessionId, Session> sessions = new HashMap<>();
        sessions.put(sessionId, session);
        sessions.put(SessionId.create(), new Session(projectId, "No path", null, null, null));
        final Path worktreePath = Path.of(worktree);
        final Path other = Path.of(System.getProperty("java.io.tmpdir"), "other");
        assertTrue(
                GitUtils.isWorktreeRegistered(sessions, worktreePath),
                "matching worktree should be detected");
        assertTrue(
                !GitUtils.isWorktreeRegistered(sessions, other),
                "different worktree should not match");
        sessions.put(SessionId.create(), new Session(projectId, "Blank", null, null, "  "));
        assertTrue(
                !GitUtils.isWorktreeRegistered(sessions, other),
                "blank worktrees should be ignored");
    }

    @Test
    void projectSessionsResolvesStoredSessionIds() throws InvalidObjectException {
        final Project project = project(DEMO_NAME, DEMO_PATH);
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId storedProjectId = state.addProject(project);
        final SessionId sessionId =
                state.addSession(
                        storedProjectId, new Session(storedProjectId, "Stored", null, null, null));
        final Project stored = state.projects().get(storedProjectId);

        assertEquals(
                List.of(Map.entry(sessionId, state.sessions().get(sessionId))),
                stored.projectSessions(state),
                "project session references should resolve through app state");
    }

    @Test
    void viewStateFactoriesPreserveSelections() {
        final ProjectId projectId = ProjectId.create();
        final SessionId sessionId = SessionId.create();
        final TerminalId terminalId = TerminalId.create();

        assertNull(ViewCoordinator.ViewState.reset().newProjectId(), "reset should clear project");
        assertEquals(
                projectId,
                ViewCoordinator.ViewState.project(projectId).newProjectId(),
                VALUE_MESSAGE);
        assertEquals(
                terminalId,
                ViewCoordinator.ViewState.projectTerminal(projectId, terminalId).newTerminalId(),
                "project terminal state should preserve terminal");
        assertEquals(
                sessionId,
                ViewCoordinator.ViewState.session(projectId, sessionId).newSessionId(),
                "session state should preserve session");
        assertEquals(
                terminalId,
                ViewCoordinator.ViewState.sessionTerminal(projectId, sessionId, terminalId)
                        .newTerminalId(),
                VALUE_MESSAGE);
    }

    @Test
    void displayTextHelpersFormatValues() {
        assertEquals("a&amp;b&lt;c&gt;", UiText.escapeHtml("a&b<c>"), VALUE_MESSAGE);
        assertEquals(
                "#0a14ff", UiText.colorHex(new Color(10, 20, 255)), "color should format as hex");
        assertEquals("Ready For Review", UiText.titleCase("READY_FOR_REVIEW"), VALUE_MESSAGE);
        assertEquals("Ready Review", UiText.titleCase("READY__REVIEW"), VALUE_MESSAGE);
        assertEquals("", UiText.titleCase("___"), VALUE_MESSAGE);
        assertEquals(Theme.successColor(), UiText.checksColor("PASSING"), VALUE_MESSAGE);
        assertEquals(Theme.dangerColor(), UiText.checksColor("FAILING"), VALUE_MESSAGE);
        assertEquals(Theme.warningColor(), UiText.checksColor("PENDING"), VALUE_MESSAGE);
        assertEquals(Theme.mutedColor(), UiText.checksColor(UNKNOWN), VALUE_MESSAGE);
    }

    @Test
    void reviewPlanAgentBuildsPromptCommandsWithAndWithoutPlaceholder() {
        final PullRequest request = pullRequest(ProjectId.create(), false, "MERGEABLE", "APPROVED");

        final String replaced =
                ReviewPlanAgent.command("agent {prompt}", "Review these", List.of(request));
        final String appended = ReviewPlanAgent.command("agent", "Review these", List.of(request));

        assertTrue(replaced.startsWith("agent "), "placeholder command should retain its command");
        assertTrue(appended.startsWith("agent "), "plain command should retain its command");
        assertTrue(replaced.contains("#1"), "prompt should include pull request details");
    }

    private static Project project(final String name, final String path) {
        return new Project(name, path, null);
    }

    private static PullRequest pullRequest(
            final ProjectId projectId,
            final boolean draft,
            final String mergeable,
            final String reviewDecision) {
        return new PullRequest(
                projectId,
                1,
                TITLE,
                DESCRIPTION,
                COMMENTS,
                "https://example.test/1",
                CREATED,
                UPDATED,
                reviewDecision,
                mergeable,
                draft,
                AUTHOR,
                FEATURE,
                1,
                1,
                "PASSING");
    }

    private static PullRequest pullRequestWithChecks(
            final ProjectId projectId,
            final boolean draft,
            final String mergeable,
            final String reviewDecision,
            final String checksStatus) {
        return new PullRequest(
                projectId,
                4,
                TITLE,
                DESCRIPTION,
                COMMENTS,
                "https://example.test/4",
                CREATED,
                UPDATED,
                reviewDecision,
                mergeable,
                draft,
                AUTHOR,
                FEATURE,
                0,
                1,
                checksStatus);
    }
}
