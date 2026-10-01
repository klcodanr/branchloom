package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.EditorCommands;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionSummary extends JPanel {
    private static final String TASK_GROUP = "Session summary";
    private static final Logger LOG = LoggerFactory.getLogger(SessionSummary.class);
    private static final String UNAVAILABLE = "Unavailable";
    private static final String AGENT_CONTEXT = "Agent context";
    private static final String EDIT_LABEL = "Edit";
    private static final String RESET_LABEL = "Reset";

    private final transient Session session;
    private final transient Project project;
    private final transient GitHub gitHub;
    private final String globalContextPath;
    private final List<Tool> editors;
    private final JTextArea branch = value("Loading branch status...");
    private final JButton pullRequest =
            UiFactory.link("Loading pull request status...", this::openPullRequest);
    private final StatusDot pullRequestStatusDot = new StatusDot(Theme.Colors.muted());
    private final JPanel pullRequestDetails = new JPanel();
    private final JPanel pullRequestHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel pullRequestMeta = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel pullRequestStatus =
            UiFactory.label("Status: Loading...", Theme.FontSize.SM);
    private final JLabel pullRequestChecks =
            UiFactory.label("Checks: Loading...", Theme.FontSize.SM);
    private final JPanel diff = new JPanel();
    private final JTextArea contextFile = textArea("Loading context file...");
    private final JTextArea contextText = textArea("Loading context...");
    private final JButton editContext = UiFactory.button(EDIT_LABEL);
    private final JButton resetContext = UiFactory.button(RESET_LABEL);
    private final JPanel contextActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final Alert cleanupAlert;
    private String pullRequestUrl;
    private boolean pullRequestClosed;
    private boolean worktreeClean;

    private record BranchStatus(String branch, boolean clean) {}

    private record PullRequestStatus(
            PullRequest request,
            PullRequestDetails details,
            PullRequestChecks checks,
            boolean checksAvailable) {}

    public SessionSummary(
            final Project project,
            final Session session,
            final AppState appState,
            final String globalContextPath,
            final List<Tool> editors,
            final Runnable removeSessionAndWorktree) {
        super();
        this.project = project;
        this.session = session;
        this.gitHub = GitHub.forProject(appState, appState.currentProjectId());
        this.globalContextPath = UiText.valueOrDefault(globalContextPath, "");
        this.editors = editors == null ? List.of() : List.copyOf(editors);
        setBorder(UiFactory.sectionBorder());
        setLayout(new BorderLayout(0, UiConstants.SPACING_XL));
        pullRequestDetails.setOpaque(false);
        pullRequestDetails.setLayout(new BoxLayout(pullRequestDetails, BoxLayout.Y_AXIS));
        pullRequestHeader.setOpaque(false);
        pullRequestHeader.add(pullRequestStatusDot);
        pullRequestHeader.add(pullRequest);
        pullRequestMeta.setOpaque(false);
        pullRequestMeta.add(pullRequestStatus);
        pullRequestMeta.add(pullRequestChecks);
        pullRequestDetails.add(pullRequestHeader);
        pullRequestDetails.add(pullRequestMeta);
        cleanupAlert =
                new Alert(
                        new Alert.Content(
                                "Ready for clean up! The pull request is finished and this "
                                        + "worktree has no uncommitted changes.",
                                Theme.Colors.success(),
                                "Remove session and worktree",
                                removeSessionAndWorktree));
        cleanupAlert.setVisible(false);
        contextActions.setOpaque(false);
        contextActions.add(editContext);
        contextActions.add(resetContext);
        editContext.addActionListener(event -> openContextInEditor());
        resetContext.addActionListener(event -> resetContext());
        contextFile.setRows(1);
        contextText.setRows(10);
        add(details(), BorderLayout.CENTER);
        refresh();
    }

    private void openPullRequest() {
        if (pullRequestUrl != null) {
            PlatformCommands.openUrl(pullRequestUrl);
        }
    }

    private JPanel details() {
        final JPanel details = UiFactory.panel();
        details.setBorder(UiFactory.sectionBorder());
        details.setLayout(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 0, UiConstants.SECTION_PADDING, UiConstants.SPACING_XL);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridy = 0;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        details.add(cleanupAlert, constraints);
        constraints.gridy = 1;
        addRow(details, constraints, 2, "Prompt", textArea(session.prompt()));
        addRow(details, constraints, 3, "Created", value(session.created().toString()));
        addRow(details, constraints, 4, "Branch", branch);
        addRow(details, constraints, 5, "Pull request", pullRequestDetails);
        addRow(details, constraints, 6, "Worktree", textArea(session.worktreePath()));
        addRow(details, constraints, 7, "Agent context file", contextFile);
        addRow(details, constraints, 8, "", contextActions);
        addRow(details, constraints, 9, AGENT_CONTEXT, contextText);
        addRow(details, constraints, 10, "Changes", diff);
        constraints.gridy = 11;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.VERTICAL;
        details.add(Box.createVerticalGlue(), constraints);
        return details;
    }

    private void addRow(
            final JPanel panel,
            final GridBagConstraints constraints,
            final int row,
            final String label,
            final JComponent value) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(UiFactory.label(label, Theme.FontSize.SM), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.gridwidth = 2;
        panel.add(value, constraints);
        constraints.gridwidth = 1;
    }

    private JTextArea textArea(final String text) {
        final JTextArea area = UiFactory.selectableText(text, Theme.FontSize.MD);
        area.setRows(2);
        area.setBorder(UiFactory.cardBorder());
        return area;
    }

    private JTextArea value(final String text) {
        final JTextArea area = UiFactory.selectableText(text, Theme.FontSize.MD);
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }

    public void refresh() {
        LOG.info(
                "Session summary refresh started: project={}, projectPath={}, session={}, worktree={}, checks=branch-status,pull-request-status,diff-summary",
                project.name(),
                project.path(),
                session.name(),
                session.worktreePath());
        diff.setOpaque(false);
        diff.setLayout(new BoxLayout(diff, BoxLayout.Y_AXIS));
        diff.removeAll();
        diff.add(value("Loading diff..."));
        contextFile.setEditable(false);
        contextFile.setText("Loading context file...");
        contextFile.setCaretPosition(0);
        contextText.setText("Loading context...");
        contextText.setCaretPosition(0);
        editContext.setText(EDIT_LABEL);
        editContext.setEnabled(false);
        resetContext.setEnabled(false);
        loadBranchStatus();
        loadPullRequestStatus();
        loadDiffSummary();
        loadContext();
    }

    private void loadContext() {
        BackgroundOperations.submit(
                        TASK_GROUP,
                        "session-agent-context",
                        () -> AgentContext.read(project, session, globalContextPath, githubUser()))
                .thenAccept(this::showContext)
                .exceptionally(
                        failure -> {
                            LOG.error("Session agent context", failure);
                            showContext(
                                    UNAVAILABLE + ": " + ErrorMessages.deepestCause(failure, ""));
                            return null;
                        });
    }

    private void showContext(final String content) {
        final Path contextPath = AgentContext.path(project, session, globalContextPath);
        contextFile.setText(contextPath == null ? "Not configured" : contextPath.toString());
        contextFile.setCaretPosition(0);
        contextText.setText(UiText.valueOrDefault(content, "No context configured."));
        contextText.setCaretPosition(0);
        final boolean configured = contextPath != null;
        editContext.setEnabled(configured);
        resetContext.setEnabled(configured);
    }

    private void openContextInEditor() {
        final Path contextPath = AgentContext.path(project, session, globalContextPath);
        if (contextPath == null) {
            return;
        }
        if (editors.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No editor is configured. Add one in Settings > Editors.",
                    AGENT_CONTEXT,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        final Tool editor = editors.getFirst();
        final String command = EditorCommands.openFile(editor, contextPath, 1, 1);
        BackgroundOperations.runCommand(
                        TASK_GROUP, "open-agent-context", command, Path.of(project.path()), null)
                .thenRun(this::loadContext)
                .exceptionally(
                        exception -> {
                            final String message =
                                    exception.getCause() == null
                                            ? exception.getMessage()
                                            : exception.getCause().getMessage();
                            JOptionPane.showMessageDialog(
                                    this,
                                    UiText.valueOrDefault(
                                            message,
                                            "Could not open the context file in the editor."),
                                    AGENT_CONTEXT,
                                    JOptionPane.ERROR_MESSAGE);
                            return null;
                        });
    }

    private void resetContext() {
        final String generated = AgentContext.generatedContent(project, session, githubUser());
        contextText.setText(generated);
        contextText.setCaretPosition(0);
        try {
            AgentContext.save(project, session, globalContextPath, generated);
            loadContext();
        } catch (IOException exception) {
            LOG.error("Reset session agent context", exception);
            JOptionPane.showMessageDialog(
                    this,
                    "Could not reset agent context: "
                            + UiText.valueOrDefault(exception.getMessage(), ""),
                    AGENT_CONTEXT,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadBranchStatus() {
        BackgroundOperations.submit(
                        TASK_GROUP,
                        "session-branch-status",
                        () -> {
                            final String worktreePath = session.worktreePath();
                            if (worktreePath == null || worktreePath.isBlank()) {
                                throw new IOException("The session has no worktree path.");
                            }
                            final Path path = Path.of(worktreePath);
                            try (GitRepository repository = GitRepository.open(path)) {
                                final String currentBranch = repository.currentBranch();
                                final var status = repository.statusSummary();
                                final boolean hasChanges =
                                        status.additions() > 0
                                                || status.modifications() > 0
                                                || status.deletions() > 0;
                                return new BranchStatus(currentBranch, !hasChanges);
                            }
                        })
                .thenAccept(
                        status -> {
                            branch.setText(
                                    (status.branch().isBlank() ? "Detached HEAD" : status.branch())
                                            + (status.clean()
                                                    ? "  ·  Clean"
                                                    : "  ·  Changes present"));
                            worktreeClean = status.clean();
                            updateCleanupSuggestion();
                        })
                .exceptionally(
                        failure -> {
                            LOG.error("Session branch status", failure);
                            branch.setText(
                                    UNAVAILABLE + ": " + ErrorMessages.deepestCause(failure, ""));
                            return null;
                        });
    }

    private void loadPullRequestStatus() {
        BackgroundOperations.submit(
                        TASK_GROUP,
                        "session-pull-request-status",
                        () -> {
                            final String worktreePath = session.worktreePath();
                            if (worktreePath == null || worktreePath.isBlank()) {
                                throw new IOException("The session has no worktree path.");
                            }
                            final Path path = Path.of(worktreePath);
                            final PullRequest request = gitHub.getPullRequest(path);
                            final PullRequestDetails pullRequestDetails =
                                    gitHub.getPullRequestDetails(request);
                            final PullRequestChecks checks = gitHub.getChecks(request);
                            return new PullRequestStatus(request, pullRequestDetails, checks, true);
                        })
                .thenAccept(
                        status -> {
                            pullRequestUrl = status.request().url().toExternalForm();
                            pullRequest.setText(
                                    "#"
                                            + status.request().number()
                                            + " "
                                            + status.request().title());
                            pullRequest.setToolTipText(pullRequestUrl);
                            pullRequestClosed =
                                    status.request().state() == PullRequest.State.CLOSED;
                            pullRequestStatus.setText(
                                    "Status: "
                                            + UiText.titleCase(status.details().status().name()));
                            pullRequestChecks.setText(
                                    status.checksAvailable()
                                            ? "Checks: "
                                                    + GitFormatter.checksPassed(status.checks())
                                            : "Checks: Unavailable");
                            updatePullRequestDot(status.details());
                            updateCleanupSuggestion();
                        })
                .exceptionally(
                        failure -> {
                            final String message = ErrorMessages.deepestCause(failure, "");
                            if (message.toLowerCase(Locale.ROOT).contains("no pull request")) {
                                pullRequestUrl = null;
                                pullRequest.setText("No pull request associated with this branch");
                                pullRequest.setToolTipText(null);
                                pullRequestClosed = false;
                                pullRequestStatusDot.update(Theme.Colors.muted(), null);
                                pullRequestStatus.setText("Status: Unavailable");
                                pullRequestChecks.setText("Checks: Unavailable");
                                pullRequestDetails.revalidate();
                                pullRequestDetails.repaint();
                                updateCleanupSuggestion();
                                return null;
                            }
                            LOG.error("Session PR status", failure);
                            pullRequest.setText(UNAVAILABLE + ": " + message);
                            pullRequestStatus.setText("Status: Unavailable");
                            pullRequestChecks.setText("Checks: Unavailable");
                            return null;
                        });
    }

    private void loadDiffSummary() {
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            GitFormatter.renderDiff(diff, UNAVAILABLE + ": The session has no worktree path.");
            return;
        }
        BackgroundOperations.runCommand(
                        TASK_GROUP,
                        "session-diff-summary",
                        "git diff --stat",
                        Path.of(worktreePath),
                        null)
                .thenAccept(diffSummary -> GitFormatter.renderDiff(diff, diffSummary))
                .exceptionally(
                        failure -> {
                            LOG.error("Session diff", failure);
                            GitFormatter.renderDiff(
                                    diff,
                                    UNAVAILABLE + ": " + ErrorMessages.deepestCause(failure, ""));
                            return null;
                        });
    }

    private void updatePullRequestDot(final PullRequestDetails details) {
        final Color color = details.indicatorColor();
        pullRequestStatusDot.update(color, null);
        pullRequestDetails.revalidate();
        pullRequestDetails.repaint();
    }

    private void updateCleanupSuggestion() {
        cleanupAlert.setVisible(pullRequestClosed && worktreeClean);
        cleanupAlert.revalidate();
        cleanupAlert.repaint();
    }

    private String githubUser() {
        try {
            return gitHub.getLogin();
        } catch (IOException exception) {
            LOG.debug("Could not resolve GitHub login for agent context", exception);
            return null;
        }
    }
}
