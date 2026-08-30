package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.GitHub;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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
    private final JComponent pullRequestStatusDot = StatusDots.create(Theme.mutedColor(), null);
    private final JPanel pullRequestDetails = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel diff = new JPanel();
    private String pullRequestUrl;

    public SessionSummary(final Project project, final Session session) {
        super();
        this.project = project;
        this.session = session;
        setBorder(new EmptyBorder(14, 14, 14, 14));
        setLayout(new BorderLayout(0, 18));
        pullRequest.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pullRequestDetails.setOpaque(false);
        pullRequestDetails.add(pullRequestStatusDot);
        pullRequestDetails.add(pullRequest);
        pullRequest.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (event.getButton() == MouseEvent.BUTTON1 && pullRequestUrl != null) {
                            openPullRequest();
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
        addRow(details, constraints, 0, "Prompt", textArea(session.prompt()));
        addRow(details, constraints, 1, "Created", value(session.created().toString()));
        addRow(details, constraints, 2, "Branch", branch);
        addRow(details, constraints, 3, "Pull request", pullRequestDetails);
        addRow(details, constraints, 4, "Worktree", textArea(session.worktreePath()));
        addRow(details, constraints, 5, "Changes", diff);
        constraints.gridy = 6;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
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
                                () ->
                                        branch.setText(
                                                (currentBranch.isBlank()
                                                                ? "Detached HEAD"
                                                                : currentBranch)
                                                        + (changes.isBlank()
                                                                ? "  ·  Clean"
                                                                : "  ·  Changes present")));
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
                                    pullRequest.setText(formatPullRequest(details));
                                    updatePullRequestDot(details);
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
                        SwingUtilities.invokeLater(() -> renderDiff(diffSummary));
                    } catch (IOException exception) {
                        reportFailure(
                                "Session diff",
                                exception,
                                message -> renderDiff(UNAVAILABLE + ": " + message));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        reportFailure(
                                "Session diff",
                                exception,
                                message -> renderDiff(UNAVAILABLE + ": " + message));
                    }
                });
    }

    private void reportFailure(
            final String source, final Exception exception, final Consumer<String> update) {
        LOG.log(Level.SEVERE, source, exception);
        final String message = exception.getMessage();
        SwingUtilities.invokeLater(() -> update.accept(message == null ? "" : message));
    }

    private void renderDiff(final String output) {
        diff.removeAll();
        if (output.isBlank()) {
            diff.add(value("No changes against head"));
        } else if (output.startsWith(UNAVAILABLE + ":")) {
            diff.add(value(output));
        } else {
            for (final String line : output.split("\\R")) {
                final String[] fields = line.split("\\t", 3);
                if (fields.length < 3) {
                    continue;
                }
                final JPanel change = new JPanel(new GridBagLayout());
                change.setOpaque(false);
                final GridBagConstraints changeConstraints = new GridBagConstraints();
                changeConstraints.anchor = GridBagConstraints.WEST;
                changeConstraints.insets = new Insets(0, 0, 0, 12);
                changeConstraints.gridx = 0;
                final JLabel additions = UiFactory.label("+" + fields[0], Theme.FontSize.XS);
                additions.setForeground(Theme.successColor());
                change.add(additions, changeConstraints);
                changeConstraints.gridx = 1;
                final JLabel deletions = UiFactory.label("-" + fields[1], Theme.FontSize.XS);
                deletions.setForeground(Theme.dangerColor());
                change.add(deletions, changeConstraints);
                changeConstraints.gridx = 2;
                changeConstraints.weightx = 1;
                changeConstraints.insets = new Insets(0, 0, 0, 0);
                change.add(UiFactory.label(fields[2], Theme.FontSize.SM), changeConstraints);
                diff.add(change);
            }
        }
        diff.revalidate();
        diff.repaint();
    }

    private static String formatPullRequest(final GitHub.PullRequestDetails details) {
        final String draft = details.draft() ? "Draft" : UiText.titleCase(details.state());
        final String review =
                details.reviewDecision().isBlank()
                        ? "Review pending"
                        : "Review: " + UiText.titleCase(details.reviewDecision());
        final String merge = mergeStatus(details.mergeState());
        final String checks =
                details.checksPassed()
                        + "/"
                        + details.checksTotal()
                        + " checks "
                        + UiText.titleCase(details.checksStatus());
        return "<html><b>#"
                + details.number()
                + "</b>  "
                + UiText.escapeHtml(details.title())
                + "<br><font color='"
                + UiText.colorHex(UIManager.getColor(UiConstants.DISABLED_FOREGROUND))
                + "'>"
                + draft
                + "  ·  "
                + review
                + "  ·  "
                + merge
                + "  ·  "
                + checks
                + "</font></html>";
    }

    private void updatePullRequestDot(final GitHub.PullRequestDetails details) {
        final Color color = UiText.checksColor(details.checksStatus());
        StatusDots.update(pullRequestStatusDot, color, null);
        pullRequestDetails.revalidate();
        pullRequestDetails.repaint();
    }

    private static String mergeStatus(final String value) {
        if ("CLEAN".equals(value)) {
            return "Can merge";
        }
        if (value.isBlank() || "UNKNOWN".equals(value)) {
            return "Mergeability unknown";
        }
        return "Cannot merge";
    }

    private void openPullRequest() {
        try {
            Desktop.getDesktop().browse(URI.create(pullRequestUrl));
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Open pull request", exception);
        }
    }
}
