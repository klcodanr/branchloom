package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.ui.dialogs.BackgroundJobDialog;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
    private final JLabel jobsStatus = UiFactory.label("", Theme.FontSize.XS);
    private final AtomicLong refreshGeneration = new AtomicLong();
    private final transient RotatingIcon refreshIcon = new RotatingIcon(UiIcons.refresh());
    private final Timer refreshAnimation;
    private final Runnable refreshCurrentViewAction;
    private boolean refreshWorkObserved;
    private int refreshAnimationTicks;
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
        refreshCurrentViewAction = refreshCurrentView;
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
        refreshButton = iconButton(refreshIcon, "Refresh current view", this::refreshCurrentView);
        refreshButton.setName("refresh-button");
        refreshButton.setVisible(false);
        refreshAnimation =
                new Timer(
                        75,
                        event -> {
                            refreshIcon.rotate();
                            refreshButton.repaint();
                            refreshAnimationTicks++;
                            final boolean active =
                                    !BackgroundTasks.summary().activeTasks().isEmpty();
                            refreshWorkObserved |= active;
                            if ((refreshWorkObserved && !active) || refreshAnimationTicks >= 20) {
                                stopRefreshAnimation();
                            }
                        });

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
        jobsProgress.setPreferredSize(new Dimension(140, 8));
        jobsProgress.setMaximumSize(new Dimension(140, 8));
        jobsProgress.setName("jobs-progress");
        jobsProgress.setVisible(false);
        jobsProgress.setToolTipText("View background job status");
        jobsProgress.addMouseListener(
                new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(final java.awt.event.MouseEvent event) {
                        showJobs();
                    }
                });
        jobsStatus.setName("jobs-status-label");
        jobsStatus.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        jobsStatus.setAlignmentX(CENTER_ALIGNMENT);
        jobsStatus.setVisible(false);
        final JPanel jobsStatusPanel = new JPanel();
        jobsStatusPanel.setOpaque(false);
        jobsStatusPanel.setLayout(new BoxLayout(jobsStatusPanel, BoxLayout.Y_AXIS));
        jobsStatusPanel.add(jobsProgress);
        jobsStatusPanel.add(Box.createVerticalStrut(UiConstants.SPACING_XS));
        jobsStatusPanel.add(jobsStatus);
        add(jobsStatusPanel, BorderLayout.EAST);
        backgroundJobs.listen(this::updateJobs);
        refresh();
    }

    private JButton iconButton(final Icon icon, final String tooltip, final Runnable action) {
        final JButton button = UiFactory.iconButton(icon, tooltip);
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
        if (visible && !BackgroundTasks.summary().activeTasks().isEmpty()) {
            startRefreshAnimation();
        }
        if (!visible) {
            stopRefreshAnimation();
        }
    }

    private void refreshCurrentView() {
        startRefreshAnimation();
        refreshCurrentViewAction.run();
    }

    private void startRefreshAnimation() {
        refreshWorkObserved = false;
        refreshAnimationTicks = 0;
        refreshAnimation.start();
    }

    private void stopRefreshAnimation() {
        refreshAnimation.stop();
        refreshAnimationTicks = 0;
        refreshIcon.reset();
        refreshButton.repaint();
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
                    final long runningCount =
                            jobs.stream()
                                    .filter(job -> job.status() == BackgroundJobs.Status.RUNNING)
                                    .count();
                    jobsProgress.setVisible(running);
                    jobsStatus.setText(runningJobCountText(runningCount));
                    jobsStatus.setVisible(running);
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

    private String runningJobCountText(final long count) {
        return count == 1 ? "1 job running" : count + " jobs running";
    }

    private void showJobs() {
        final JPopupMenu menu = createJobsMenu();
        menu.show(jobsProgress, 0, -menu.getPreferredSize().height);
    }

    protected JPopupMenu createJobsMenu() {
        final JPopupMenu menu = new JPopupMenu();
        if (jobs.isEmpty()) {
            menu.add("No background jobs").setEnabled(false);
        } else {
            final JLabel heading = UiFactory.label("Background jobs", Theme.FontSize.MD);
            heading.setFont(Theme.boldFont(Theme.FontSize.MD));
            heading.setBorder(
                    BorderFactory.createEmptyBorder(
                            UiConstants.SPACING_XS,
                            UiConstants.CONTENT_PADDING,
                            UiConstants.SPACING_XS,
                            UiConstants.CONTENT_PADDING));
            menu.add(heading);
            menu.addSeparator();
            jobs.stream()
                    .sorted(Comparator.comparing(BackgroundJobs.Job::title))
                    .map(this::jobSelector)
                    .forEach(menu::add);
        }
        return menu;
    }

    private JMenuItem jobSelector(final BackgroundJobs.Job job) {
        final JMenuItem item = new JMenuItem(job.title() + "  ·  " + jobStatusText(job.status()));
        item.setToolTipText(jobSelectorTooltip(job));
        item.addActionListener(event -> BackgroundJobDialog.show(this, job));
        return item;
    }

    private String jobSelectorTooltip(final BackgroundJobs.Job job) {
        final String context =
                job.project().isBlank()
                        ? ""
                        : "Project: "
                                + job.project()
                                + (job.session().isBlank() ? "" : "  ·  Session: " + job.session());
        return context.isBlank() ? job.message() : context + "  ·  " + job.message();
    }

    private String jobStatusText(final BackgroundJobs.Status status) {
        return switch (status) {
            case RUNNING -> "Running";
            case SUCCEEDED -> "Completed";
            case FAILED -> "Failed";
        };
    }
}
