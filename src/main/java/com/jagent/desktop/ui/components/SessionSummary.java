package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.EditorCommands;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.PlatformCommands;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public final class SessionSummary extends JPanel {
    private static final String TASK_GROUP = "Session summary";
    private static final Logger LOG = Logger.getLogger(SessionSummary.class.getName());
    private static final String UNAVAILABLE = "Unavailable";
    private static final String AGENT_CONTEXT = "Agent context";
    private static final String EDIT_LABEL = "Edit";
    private static final String RESET_LABEL = "Reset";
    private final transient Session session;
    private final transient Project project;
    private final String globalContextPath;
    private final List<Tool> editors;
    private final JTextArea branch = value("Loading branch status...");
    private final JButton pullRequest =
            UiFactory.link("Loading pull request status...", this::openPullRequest);
    private final StatusDot pullRequestStatusDot = new StatusDot(Theme.mutedColor());
    private final JPanel pullRequestDetails = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
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

    public SessionSummary(final Project project, final Session session) {
        this(project, session, "", List.of(), () -> {});
    }

    public SessionSummary(
            final Project project, final Session session, final Runnable removeSessionAndWorktree) {
        this(project, session, "", List.of(), removeSessionAndWorktree);
    }

    public SessionSummary(
            final Project project,
            final Session session,
            final String globalContextPath,
            final Runnable removeSessionAndWorktree) {
        this(project, session, globalContextPath, List.of(), removeSessionAndWorktree);
    }

    public SessionSummary(
            final Project project,
            final Session session,
            final String globalContextPath,
            final List<Tool> editors,
            final Runnable removeSessionAndWorktree) {
        super();
        this.project = project;
        this.session = session;
        this.globalContextPath = globalContextPath == null ? "" : globalContextPath;
        this.editors = editors == null ? List.of() : List.copyOf(editors);
        setBorder(UiFactory.sectionBorder());
        setLayout(new BorderLayout(0, UiConstants.SPACING_XL));
        pullRequestDetails.setOpaque(false);
        pullRequestDetails.add(pullRequestStatusDot);
        pullRequestDetails.add(pullRequest);
        cleanupAlert =
                new Alert(
                        new Alert.Content(
                                "Ready for clean up! The pull request is finished and this "
                                        + "worktree has no uncommitted changes.",
                                Theme.successColor(),
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
                () ->
                        "Session summary refresh started: project="
                                + project.name()
                                + ", projectPath="
                                + project.path()
                                + ", session="
                                + session.name()
                                + ", worktree="
                                + session.worktreePath()
                                + ", checks=branch-status,pull-request-status,diff-summary");
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
        BackgroundTasks.submit(
                TASK_GROUP,
                "session-agent-context",
                () -> {
                    try {
                        final String content =
                                AgentContext.read(project, session, globalContextPath);
                        SwingUtilities.invokeLater(() -> showContext(content));
                    } catch (IOException exception) {
                        reportFailure(
                                "Session agent context",
                                exception,
                                message -> showContext(UNAVAILABLE + ": " + message));
                    }
                });
    }

    private void showContext(final String content) {
        final Path contextPath = AgentContext.path(project, session, globalContextPath);
        contextFile.setText(contextPath == null ? "Not configured" : contextPath.toString());
        contextFile.setCaretPosition(0);
        contextText.setText(
                content == null || content.isBlank() ? "No context configured." : content);
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
        CommandRunner.run(
                command,
                Path.of(project.path()),
                this::loadContext,
                output ->
                        JOptionPane.showMessageDialog(
                                this,
                                output == null || output.isBlank()
                                        ? "Could not open the context file in the editor."
                                        : output,
                                AGENT_CONTEXT,
                                JOptionPane.ERROR_MESSAGE));
    }

    private void resetContext() {
        final String generated = AgentContext.generatedContent(project, session);
        contextText.setText(generated);
        contextText.setCaretPosition(0);
        try {
            AgentContext.save(project, session, globalContextPath, generated);
            loadContext();
        } catch (IOException exception) {
            LOG.log(Level.SEVERE, "Reset session agent context", exception);
            JOptionPane.showMessageDialog(
                    this,
                    "Could not reset agent context: "
                            + (exception.getMessage() == null ? "" : exception.getMessage()),
                    AGENT_CONTEXT,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadBranchStatus() {
        BackgroundTasks.submit(
                TASK_GROUP,
                "session-branch-status",
                () -> {
                    try {
                        final Path worktree = Path.of(session.worktreePath());
                        final String currentBranch = Git.currentBranch(worktree);
                        final String changes = Git.status(worktree);
                        SwingUtilities.invokeLater(
                                () -> {
                                    branch.setText(
                                            (currentBranch.isBlank()
                                                            ? "Detached HEAD"
                                                            : currentBranch)
                                                    + (changes.isBlank()
                                                            ? "  ·  Clean"
                                                            : "  ·  Changes present"));
                                    worktreeClean = changes.isBlank();
                                    updateCleanupSuggestion();
                                });
                    } catch (IOException exception) {
                        reportFailure(
                                "Session branch status",
                                exception,
                                message -> branch.setText(UNAVAILABLE + ": " + message));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        reportFailure(
                                "Session branch status",
                                exception,
                                message -> branch.setText(UNAVAILABLE + ": " + message));
                    }
                });
    }

    private void loadPullRequestStatus() {
        BackgroundTasks.submit(
                TASK_GROUP,
                "session-pull-request-status",
                () -> {
                    try {
                        final GitHub.PullRequestDetails details =
                                GitHub.loadCurrent(project, Path.of(session.worktreePath()));
                        SwingUtilities.invokeLater(
                                () -> {
                                    pullRequestUrl = details.url();
                                    pullRequest.setText(GitFormatter.detailsHtml(details));
                                    updatePullRequestDot(details);
                                    pullRequestClosed =
                                            "CLOSED".equals(details.state())
                                                    || "MERGED".equals(details.state());
                                    updateCleanupSuggestion();
                                });
                    } catch (IOException exception) {
                        final String message =
                                exception.getMessage() == null ? "" : exception.getMessage();
                        if (message.toLowerCase(Locale.ROOT).contains("no pull request")) {
                            SwingUtilities.invokeLater(
                                    () -> {
                                        pullRequestUrl = null;
                                        pullRequest.setText(
                                                "No pull request associated with this branch");
                                        pullRequest.setToolTipText(null);
                                    });
                        } else {
                            LOG.log(Level.SEVERE, "Session PR status", exception);
                            SwingUtilities.invokeLater(
                                    () -> pullRequest.setText(UNAVAILABLE + ": " + message));
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        LOG.log(Level.SEVERE, "Session PR status", exception);
                        SwingUtilities.invokeLater(
                                () -> pullRequest.setText("Pull request lookup interrupted"));
                    }
                });
    }

    private void loadDiffSummary() {
        BackgroundTasks.submit(
                TASK_GROUP,
                "session-diff-summary",
                () -> {
                    try {
                        final String diffSummary = Git.diffSummary(Path.of(session.worktreePath()));
                        SwingUtilities.invokeLater(
                                () -> GitFormatter.renderDiff(diff, diffSummary));
                    } catch (IOException exception) {
                        reportFailure(
                                "Session diff",
                                exception,
                                message ->
                                        GitFormatter.renderDiff(
                                                diff, UNAVAILABLE + ": " + message));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        reportFailure(
                                "Session diff",
                                exception,
                                message ->
                                        GitFormatter.renderDiff(
                                                diff, UNAVAILABLE + ": " + message));
                    }
                });
    }

    private void reportFailure(
            final String source, final Exception exception, final Consumer<String> update) {
        LOG.log(Level.SEVERE, source, exception);
        final String message = exception.getMessage();
        SwingUtilities.invokeLater(() -> update.accept(message == null ? "" : message));
    }

    private void updatePullRequestDot(final GitHub.PullRequestDetails details) {
        final Color color = UiText.checksColor(details.checksStatus());
        pullRequestStatusDot.update(color, null);
        pullRequestDetails.revalidate();
        pullRequestDetails.repaint();
    }

    private void updateCleanupSuggestion() {
        cleanupAlert.setVisible(pullRequestClosed && worktreeClean);
        cleanupAlert.revalidate();
        cleanupAlert.repaint();
    }
}
