package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.Git;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Compact bottom bar for problems, workspace status, and background jobs. */
public final class BottomBar extends JPanel {
    private final transient AppState appState;
    private final JButton settingsButton;
    private final JButton homeButton;
    private final JButton searchButton;
    private final JButton problemsButton;
    private final JButton refreshButton;
    private final JLabel project = UiFactory.label("", Theme.FontSize.XS);
    private final JLabel branchIcon = new JLabel(UiIcons.gitBranch());
    private final JLabel branch = UiFactory.label("", Theme.FontSize.XS);
    private final GitStatusPanel gitStatus = new GitStatusPanel();
    private final JProgressBar jobsProgress = new JProgressBar();
    private final AtomicLong refreshGeneration = new AtomicLong();
    private List<BackgroundJobs.Job> jobs = List.of();

    public BottomBar(
            final AppState appState,
            final BackgroundJobs backgroundJobs,
            final Runnable openHome,
            final Runnable openSettings,
            final Runnable openSearch,
            final Runnable openProblems,
            final Runnable refreshCurrentView) {
        super(new BorderLayout(12, 0));
        this.appState = appState;
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(
                                1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                        BorderFactory.createEmptyBorder(
                                UiConstants.SPACING_XS,
                                UiConstants.CONTENT_PADDING,
                                UiConstants.SPACING_XS,
                                UiConstants.CONTENT_PADDING)));

        homeButton = iconButton(UiIcons.home(), "Go to home", openHome);
        homeButton.setName("home-button");
        settingsButton = iconButton(UiIcons.settings(), "Open settings", openSettings);
        settingsButton.setName("settings-button");
        searchButton =
                iconButton(UiIcons.search(), "Find projects, sessions, or terminals", openSearch);
        searchButton.setName("search-button");
        problemsButton = iconButton(UiIcons.triangleAlert(), "Open problems", openProblems);
        problemsButton.setName("problems-button");
        refreshButton = iconButton(UiIcons.refresh(), "Refresh current view", refreshCurrentView);
        refreshButton.setName("refresh-button");
        refreshButton.setVisible(false);

        final JPanel left =
                new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.CONTENT_PADDING, 0));
        left.setOpaque(false);
        left.add(homeButton);
        left.add(settingsButton);
        left.add(searchButton);
        left.add(problemsButton);
        left.add(refreshButton);
        left.add(project);
        branchIcon.setVisible(false);
        left.add(branchIcon);
        left.add(branch);
        left.add(gitStatus);
        add(left, BorderLayout.WEST);

        jobsProgress.setIndeterminate(true);
        jobsProgress.setPreferredSize(new java.awt.Dimension(140, 8));
        jobsProgress.setVisible(false);
        jobsProgress.setToolTipText("View background job status");
        jobsProgress.addMouseListener(
                new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(final java.awt.event.MouseEvent event) {
                        showJobs();
                    }
                });
        add(jobsProgress, BorderLayout.EAST);
        backgroundJobs.listen(this::updateJobs);
        refresh();
    }

    private JButton iconButton(
            final javax.swing.Icon icon, final String tooltip, final Runnable action) {
        final JButton button = UiFactory.iconButton(icon);
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.addActionListener(event -> action.run());
        return button;
    }

    public void refresh() {
        final long generation = refreshGeneration.incrementAndGet();
        final Project currentProject = appState.currentProject();
        final Session currentSession = appState.currentSession();
        if (currentProject == null) {
            clearWorkspaceStatus();
            return;
        }
        final Path worktree =
                currentSession == null || currentSession.worktreePath() == null
                        ? Path.of(currentProject.path())
                        : Path.of(currentSession.worktreePath());
        project.setText(currentProject.name());
        BackgroundTasks.submit(
                "Status bar",
                "git-status",
                () -> {
                    try {
                        final String currentBranch = Git.currentBranch(worktree).trim();
                        final Git.WorktreeStatus status = Git.worktreeStatus(worktree, false);
                        SwingUtilities.invokeLater(
                                () ->
                                        updateGitStatusIfCurrent(
                                                generation,
                                                currentProject.name(),
                                                currentBranch,
                                                status));
                    } catch (java.io.IOException exception) {
                        SwingUtilities.invokeLater(
                                () ->
                                        updateGitStatusIfCurrent(
                                                generation, currentProject.name(), "", null));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        SwingUtilities.invokeLater(
                                () ->
                                        updateGitStatusIfCurrent(
                                                generation, currentProject.name(), "", null));
                    }
                });
    }

    public void setRefreshVisible(final boolean visible) {
        refreshButton.setVisible(visible);
    }

    private void clearWorkspaceStatus() {
        project.setText("");
        branchIcon.setVisible(false);
        branch.setText("");
        gitStatus.removeAll();
        revalidate();
        repaint();
    }

    private void updateGitStatus(
            final String projectName, final String branchName, final Git.WorktreeStatus status) {
        project.setText(projectName == null ? "" : projectName);
        final boolean hasBranch = branchName != null && !branchName.isBlank();
        branchIcon.setVisible(hasBranch);
        branch.setText(hasBranch ? branchName : "");
        if (status == null) {
            gitStatus.showUnavailable("Git unavailable");
        } else {
            gitStatus.showStatus(status);
        }
        revalidate();
        repaint();
    }

    private void updateGitStatusIfCurrent(
            final long generation,
            final String projectName,
            final String branchName,
            final Git.WorktreeStatus status) {
        if (generation == refreshGeneration.get()) {
            updateGitStatus(projectName, branchName, status);
        }
    }

    private void updateJobs(final List<BackgroundJobs.Job> updatedJobs) {
        final Runnable update =
                () -> {
                    jobs = updatedJobs;
                    final boolean running =
                            jobs.stream()
                                    .anyMatch(job -> job.status() == BackgroundJobs.Status.RUNNING);
                    jobsProgress.setVisible(running);
                    jobsProgress.setToolTipText(
                            running ? runningJobText() : "View background job status");
                };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private String runningJobText() {
        return jobs.stream()
                .filter(job -> job.status() == BackgroundJobs.Status.RUNNING)
                .map(job -> job.title() + ": " + job.message())
                .sorted()
                .findFirst()
                .orElse("View background job status");
    }

    private void showJobs() {
        final JPopupMenu menu = new JPopupMenu();
        if (jobs.isEmpty()) {
            menu.add("No background jobs").setEnabled(false);
        } else {
            jobs.stream()
                    .sorted(Comparator.comparing(BackgroundJobs.Job::title))
                    .forEach(
                            job ->
                                    menu.add(
                                            job.title()
                                                    + " - "
                                                    + job.status()
                                                    + ": "
                                                    + job.message()));
        }
        menu.show(jobsProgress, 0, -menu.getPreferredSize().height);
    }
}
