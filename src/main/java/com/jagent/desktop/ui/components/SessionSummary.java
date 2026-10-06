package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionSummary extends ScrollablePanel {
    public static final int LABEL_WIDTH = 80;
    private static final Logger LOG = LoggerFactory.getLogger(SessionSummary.class);

    private final transient Session session;
    private final transient Project project;
    private final String globalContextPath;
    private final SessionBranchPanel branch;
    private final SessionPullRequestPanel pullRequest;
    private final SessionChangesSection changes;
    private final SessionAgentContextSection agentContext;
    private final Alert cleanupAlert;
    private JPanel detailsPanel;
    private boolean pullRequestClosed;
    private boolean worktreeClean;

    public SessionSummary(
            final Project project,
            final Session session,
            final AppState appState,
            final String globalContextPath,
            final Runnable removeSessionAndWorktree) {
        super();
        this.project = project;
        this.session = session;
        final GitHub gitHub = GitHub.forProject(appState, appState.currentProjectId());
        this.globalContextPath = UiText.valueOrDefault(globalContextPath, "");
        branch = new SessionBranchPanel(this::branchCleanChanged);
        pullRequest = new SessionPullRequestPanel(session, gitHub, this::pullRequestClosedChanged);
        changes = new SessionChangesSection();
        agentContext =
                new SessionAgentContextSection(project, session, this.globalContextPath, gitHub);
        changes.onVisibilityChanged(visible -> updateOptionalSection(changes, visible, 7));
        agentContext.onVisibilityChanged(
                visible -> updateOptionalSection(agentContext, visible, 8));
        setBorder(UiBorders.section());
        setLayout(new BorderLayout(0, UiConstants.SPACING_XL));
        cleanupAlert =
                new Alert(
                        new Alert.Content(
                                "Ready for clean up! The pull request is finished and this "
                                        + "worktree has no uncommitted changes.",
                                Theme.Colors.success(),
                                "Remove session and worktree",
                                removeSessionAndWorktree));
        cleanupAlert.setVisible(false);
        add(details(), BorderLayout.CENTER);
        refresh();
    }

    private JPanel details() {
        final JPanel details = new JPanel();
        detailsPanel = details;
        details.setBorder(UiBorders.section());
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
        addRow(details, constraints, 2, "Pull request", pullRequest);
        addRow(details, constraints, 3, "Created", value(UiText.relativeTime(session.created())));
        addRow(details, constraints, 4, "Branch", branch);
        addRow(details, constraints, 5, "Worktree", textArea(session.worktreePath()));
        addRow(details, constraints, 6, "Prompt", scrollablePrompt());
        constraints.gridy = 11;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.VERTICAL;
        details.add(Box.createVerticalGlue(), constraints);
        return details;
    }

    private JScrollPane scrollablePrompt() {
        final JScrollPane scroll = new JScrollPane(textArea(session.prompt()));
        scroll.setPreferredSize(new Dimension(0, 170));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private JLabel addRow(
            final JPanel panel,
            final GridBagConstraints constraints,
            final int row,
            final String label,
            final JComponent value) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.gridwidth = 3;
        final JPanel rowPanel = new JPanel(new BorderLayout(UiConstants.SPACING_MD, 0));
        rowPanel.setOpaque(false);
        final JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(Theme.font(Theme.FontSize.SM));
        labelComponent.setPreferredSize(
                new Dimension(LABEL_WIDTH, labelComponent.getPreferredSize().height));
        rowPanel.add(labelComponent, BorderLayout.WEST);
        rowPanel.add(value, BorderLayout.CENTER);
        panel.add(rowPanel, constraints);
        constraints.gridwidth = 1;
        return labelComponent;
    }

    private JTextArea textArea(final String text) {
        final JTextArea area = new SelectableTextLabel(text, Theme.FontSize.MD);
        area.setRows(2);
        area.setBorder(UiBorders.card());
        return area;
    }

    private JTextArea value(final String text) {
        final JTextArea area = new SelectableTextLabel(text, Theme.FontSize.MD);
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
        removeOptionalSections();
        loadWorktreeStatus();
        pullRequest.load();
        agentContext.load();
    }

    private void loadWorktreeStatus() {
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            final String message = "Unavailable: The session has no worktree path.";
            branch.showUnavailable(message);
            changes.showUnavailable(message);
            return;
        }
        if (!Files.isDirectory(Path.of(worktreePath))) {
            final String message = "Unavailable: The session worktree does not exist.";
            branch.showUnavailable(message);
            changes.showUnavailable(message);
            return;
        }
        changes.showLoading();
        BackgroundOperations.submit(
                        "Session summary",
                        "session-worktree-status",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(worktreePath))) {
                                return repository.worktreeStatus();
                            }
                        })
                .thenAccept(
                        status -> {
                            branch.show(status);
                            changes.show(status);
                        })
                .exceptionally(
                        failure -> {
                            final String message =
                                    "Unavailable: " + ErrorMessages.deepestCause(failure, "");
                            branch.showUnavailable(message);
                            changes.showUnavailable(message);
                            return null;
                        });
    }

    private void removeOptionalSections() {
        detailsPanel.remove(changes);
        detailsPanel.remove(agentContext);
        detailsPanel.revalidate();
        detailsPanel.repaint();
    }

    private void addOptionalSection(final JComponent section, final int row) {
        if (section.getParent() == null) {
            final GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = row;
            constraints.gridx = 0;
            constraints.gridwidth = 3;
            constraints.weightx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets =
                    new Insets(0, 0, UiConstants.SECTION_PADDING, UiConstants.SPACING_XL);
            detailsPanel.add(section, constraints);
        }
        detailsPanel.revalidate();
        detailsPanel.repaint();
    }

    private void updateOptionalSection(
            final JComponent section, final boolean visible, final int row) {
        if (visible) {
            addOptionalSection(section, row);
        } else {
            detailsPanel.remove(section);
            detailsPanel.revalidate();
            detailsPanel.repaint();
        }
    }

    private void branchCleanChanged(final boolean clean) {
        worktreeClean = clean;
        updateCleanupSuggestion();
    }

    private void pullRequestClosedChanged(final boolean closed) {
        pullRequestClosed = closed;
        updateCleanupSuggestion();
    }

    private void updateCleanupSuggestion() {
        cleanupAlert.setVisible(pullRequestClosed && worktreeClean);
        cleanupAlert.revalidate();
        cleanupAlert.repaint();
    }
}
