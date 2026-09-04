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
    private final JButton problemsButton;
    private final JLabel project = UiFactory.label("", Theme.FontSize.XS);
    private final JLabel branch = UiFactory.label("", Theme.FontSize.XS);
    private final JPanel gitStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JProgressBar jobsProgress = new JProgressBar();
    private final AtomicLong refreshGeneration = new AtomicLong();
    private List<BackgroundJobs.Job> jobs = List.of();

    public BottomBar(
            final AppState appState,
            final BackgroundJobs backgroundJobs,
            final Runnable openProblems) {
        super(new BorderLayout(12, 0));
        this.appState = appState;
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(
                                1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        problemsButton = UiFactory.iconButton(UiIcons.triangleAlert());
        problemsButton.setToolTipText("Open problems");
        problemsButton.getAccessibleContext().setAccessibleName("Open problems");
        problemsButton.addActionListener(event -> openProblems.run());

        final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(problemsButton);
        left.add(project);
        left.add(branch);
        gitStatus.setOpaque(false);
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
                        final Git.WorktreeStatus status = Git.worktreeStatus(worktree);
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

    private void clearWorkspaceStatus() {
        project.setText("");
        branch.setText("");
        gitStatus.removeAll();
        revalidate();
        repaint();
    }

    private void updateGitStatus(
            final String projectName, final String branchName, final Git.WorktreeStatus status) {
        project.setText(projectName == null ? "" : projectName);
        branch.setText(branchName == null || branchName.isBlank() ? "" : "branch " + branchName);
        gitStatus.removeAll();
        if (status == null) {
            gitStatus.add(UiFactory.label("Git unavailable", Theme.FontSize.XS));
        } else if (status.files().isEmpty()) {
            final JLabel clean = UiFactory.label("Clean", Theme.FontSize.XS);
            clean.setForeground(Theme.mutedColor());
            gitStatus.add(clean);
        } else {
            addCount(status.additions(), "+", Theme.successColor());
            addCount(status.modifications(), "~", Theme.warningColor());
            addCount(status.deletions(), "-", Theme.dangerColor());
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

    private void addCount(final int count, final String prefix, final java.awt.Color color) {
        if (count == 0) {
            return;
        }
        final JLabel label = UiFactory.label(prefix + count, Theme.FontSize.XS);
        label.setForeground(color);
        gitStatus.add(label);
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
