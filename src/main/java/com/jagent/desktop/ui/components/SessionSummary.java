package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.PlatformCommands;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public final class SessionSummary extends JPanel {
    private static final String TASK_GROUP = "Session summary";
    private static final Logger LOG = Logger.getLogger(SessionSummary.class.getName());
    private static final String UNAVAILABLE = "Unavailable";
    private final transient Session session;
    private final transient Project project;
    private final JTextArea branch = value("Loading branch status...");
    private final JTextPane pullRequest =
            UiFactory.selectableHtml("Loading pull request status...", Theme.FontSize.MD);
    private final StatusDot pullRequestStatusDot = new StatusDot(Theme.mutedColor());
    private final JPanel pullRequestDetails = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel diff = new JPanel();
    private final Alert cleanupAlert;
    private String pullRequestUrl;
    private boolean pullRequestClosed;
    private boolean worktreeClean;

    public SessionSummary(final Project project, final Session session) {
        this(project, session, () -> {});
    }

    public SessionSummary(
            final Project project, final Session session, final Runnable removeSessionAndWorktree) {
        super();
        this.project = project;
        this.session = session;
        setBorder(new EmptyBorder(14, 14, 14, 14));
        setLayout(new BorderLayout(0, 18));
        pullRequest.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pullRequestDetails.setOpaque(false);
        pullRequestDetails.add(pullRequestStatusDot);
        pullRequestDetails.add(pullRequest);
        cleanupAlert =
                new Alert(
                        new Alert.Content(
                                "Ready for clean up! The pull request is finished and this "
                                        + "worktree has no uncommitted changes.",
                                removeSessionAndWorktree));
        cleanupAlert.setVisible(false);
        pullRequest.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (event.getButton() == MouseEvent.BUTTON1 && pullRequestUrl != null) {
                            PlatformCommands.openUrl(pullRequestUrl);
                        }
                    }
                });
        add(header(), BorderLayout.NORTH);
        add(details(), BorderLayout.CENTER);
        loadStatus();
    }

    private JPanel header() {
        final JPanel header = UiFactory.panel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiFactory.label(TASK_GROUP, Theme.FontSize.XL));
        header.add(Box.createVerticalStrut(5));
        header.add(UiFactory.label("Workspace and pull request status", Theme.FontSize.SM));
        return header;
    }

    private JPanel details() {
        final JPanel details = UiFactory.panel();
        details.setBorder(new EmptyBorder(16, 16, 16, 16));
        details.setLayout(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 0, 16, 18);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridy = 0;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addRow(details, constraints, 1, "Prompt", textArea(session.prompt()));
        addRow(details, constraints, 2, "Created", value(session.created().toString()));
        addRow(details, constraints, 3, "Branch", branch);
        addRow(details, constraints, 4, "Pull request", pullRequestDetails);
        addRow(details, constraints, 5, "Worktree", textArea(session.worktreePath()));
        addRow(details, constraints, 6, "Changes", diff);
        constraints.gridy = 7;
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
        final JTextArea area = new JTextArea(text == null ? "" : text);
        UiFactory.configureTextAreaTraversal(area);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(2);
        area.setBorder(new EmptyBorder(8, 8, 8, 8));
        return area;
    }

    private JTextArea value(final String text) {
        final JTextArea area = UiFactory.selectableText(text, Theme.FontSize.MD);
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }

    private void loadStatus() {
        diff.setOpaque(false);
        diff.setLayout(new BoxLayout(diff, BoxLayout.Y_AXIS));
        diff.add(value("Loading diff..."));
        loadBranchStatus();
        loadPullRequestStatus();
        loadDiffSummary();
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
