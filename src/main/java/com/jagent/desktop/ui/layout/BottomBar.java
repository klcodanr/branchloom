package com.jagent.desktop.ui.layout;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.jagent.desktop.api.RefreshableView;
import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.git.WorktreeStatusSummary;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.actions.FindAction;
import com.jagent.desktop.ui.actions.OpenSettingsAction;
import com.jagent.desktop.ui.actions.ProblemsAction;
import com.jagent.desktop.ui.components.GitStatusPanel;
import com.jagent.desktop.ui.components.IconButton;
import com.jagent.desktop.ui.components.RefreshButton;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.UiPopupMenus;
import com.jagent.desktop.ui.components.UiText;
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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/** Compact bottom bar for problems, workspace status, and background jobs. */
public final class BottomBar extends JPanel {
    private final transient ViewController viewController;

    private final JLabel project = new JLabel();
    private final JLabel branchIcon = new JLabel(UiIcons.gitBranch());
    private final JLabel branch = new JLabel();
    private final GitStatusPanel gitStatus = new GitStatusPanel();
    private final JProgressBar jobsProgress = new JProgressBar();
    private final transient BackgroundJobDialog backgroundJobDialog;
    private final JLabel jobsStatus = new JLabel();
    private final AtomicLong refreshGeneration = new AtomicLong();
    private final RefreshButton refreshButton = new RefreshButton();
    private List<BackgroundJobs.Job> jobs = List.of();

    private record StatusUpdate(WorktreeStatusSummary worktreeStatusSummary, String branchName) {}

    @Inject
    public BottomBar(
            final ViewController viewController,
            final AppState appState,
            final BackgroundJobs backgroundJobs,
            final @Named("dataDirectory") Path dataDirectory,
            final DialogService dialogService) {
        super(new BorderLayout(12, 0));
        this.viewController = viewController;
        this.backgroundJobDialog = new BackgroundJobDialog(dialogService);
        project.setFont(Theme.font(Theme.FontSize.XS));
        branch.setFont(Theme.font(Theme.FontSize.XS));
        jobsStatus.setFont(Theme.font(Theme.FontSize.XS));
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.Colors.border()),
                        BorderFactory.createEmptyBorder(
                                UiConstants.SPACING_XS,
                                UiConstants.CONTENT_PADDING,
                                UiConstants.SPACING_XS,
                                UiConstants.CONTENT_PADDING)));

        final JButton homeButton = new IconButton(UiIcons.home(), "Go to home");
        homeButton.addActionListener(event -> viewController.updateView(ViewId.HOME, null));
        homeButton.setName("home-button");
        final JButton settingsButton = new IconButton(UiIcons.settings(), "Open settings");
        settingsButton.addActionListener(
                event -> new OpenSettingsAction(appState, dataDirectory, dialogService).execute());
        settingsButton.setName("settings-button");
        final JButton searchButton =
                new IconButton(UiIcons.search(), "Find projects, sessions, or terminals");
        searchButton.addActionListener(
                event -> new FindAction(viewController, dialogService).execute());
        searchButton.setName("search-button");
        final JButton problemsButton = new IconButton(UiIcons.triangleAlert(), "Open problems");
        problemsButton.addActionListener(event -> new ProblemsAction(viewController).execute());
        problemsButton.setName("problems-button");
        refreshButton.addActionListener(
                event ->
                        this.viewController
                                .refreshCurrentView()
                                .whenComplete((r, e) -> this.refreshButton.stop()));
        refreshButton.setName("refresh-button");
        refreshButton.setVisible(false);
        viewController.onViewChanged(
                ignored -> {
                    refresh();
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
        jobsStatus.setForeground(Theme.Colors.muted());
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

    public void updateView(final View view) {
        updateRefreshVisibility(view);
    }

    public void refresh() {
        final long generation = refreshGeneration.incrementAndGet();
        final Project currentProject = viewController.currentProject();
        final Session currentSession = viewController.currentSession();
        if (currentProject == null) {
            clearWorkspaceStatus();
            return;
        }
        clearGitStatus();
        final Path worktree =
                currentSession == null || currentSession.worktreePath() == null
                        ? Path.of(currentProject.path())
                        : Path.of(currentSession.worktreePath());
        project.setText(currentProject.name());
        BackgroundOperations.submit(
                        "Status bar",
                        "git-status",
                        () -> {
                            try (GitRepository gitRepository = GitRepository.open(worktree)) {
                                final String currentBranch = gitRepository.currentBranch();
                                final WorktreeStatusSummary status = gitRepository.statusSummary();
                                return new StatusUpdate(status, currentBranch);
                            }
                        })
                .thenAccept(
                        (statusUpdate) ->
                                updateGitStatusIfCurrent(
                                        generation,
                                        currentProject.name(),
                                        statusUpdate.branchName(),
                                        statusUpdate.worktreeStatusSummary()))
                .exceptionally(
                        (ex) -> {
                            updateGitStatusIfCurrent(generation, currentProject.name(), "", null);
                            return null;
                        });
    }

    private void updateRefreshVisibility(final View view) {
        final boolean visible = view instanceof RefreshableView;
        refreshButton.stop();
        refreshButton.setVisible(visible);
    }

    private void clearWorkspaceStatus() {
        project.setText("");
        clearGitStatus();
        revalidate();
        repaint();
    }

    private void clearGitStatus() {
        branchIcon.setVisible(false);
        branch.setText("");
        gitStatus.removeAll();
    }

    private void updateGitStatus(
            final String projectName, final String branchName, final WorktreeStatusSummary status) {
        project.setText(UiText.valueOrDefault(projectName, ""));
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
            final WorktreeStatusSummary status) {
        final Runnable update =
                () -> {
                    if (generation == refreshGeneration.get()) {
                        updateGitStatus(projectName, branchName, status);
                    }
                };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
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
        UiPopupMenus.show(menu, jobsProgress, 0, -menu.getPreferredSize().height);
    }

    protected JPopupMenu createJobsMenu() {
        final JPopupMenu menu = new JPopupMenu();
        if (jobs.isEmpty()) {
            menu.add("No background jobs").setEnabled(false);
        } else {
            final JLabel heading = new JLabel("Background jobs");
            heading.setFont(Theme.font(Theme.FontSize.MD));
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
        final JMenuItem item =
                new JMenuItem(job.title() + "  ·  " + UiText.titleCase(job.status().name()));
        item.setToolTipText(jobSelectorTooltip(job));
        item.addActionListener(event -> backgroundJobDialog.show(job));
        return item;
    }

    private String jobSelectorTooltip(final BackgroundJobs.Job job) {
        final String project =
                job.project().isBlank()
                        ? ""
                        : "Project: "
                                + job.project()
                                + (job.session().isBlank() ? "" : "  ·  Session: " + job.session());
        return project.isBlank() ? job.message() : project + "  ·  " + job.message();
    }
}
