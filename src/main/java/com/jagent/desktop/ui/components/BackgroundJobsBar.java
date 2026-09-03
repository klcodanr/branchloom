package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.BackgroundJobs;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/** Bottom-bar affordance for inspecting user-visible background jobs. */
public final class BackgroundJobsBar extends JPanel {
    private final JButton jobsButton = new JButton();
    private List<BackgroundJobs.Job> jobs = List.of();

    public BackgroundJobsBar(final BackgroundJobs backgroundJobs) {
        super(new BorderLayout());
        setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        final JPanel contents = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        contents.setOpaque(false);
        jobsButton.setBorderPainted(false);
        jobsButton.setContentAreaFilled(false);
        jobsButton.setFocusPainted(false);
        jobsButton.addActionListener(event -> showJobs());
        contents.add(jobsButton);
        add(contents, BorderLayout.WEST);
        backgroundJobs.listen(this::updateJobs);
    }

    private void updateJobs(final List<BackgroundJobs.Job> updatedJobs) {
        final Runnable update =
                () -> {
                    jobs = updatedJobs;
                    final long running =
                            jobs.stream()
                                    .filter(job -> job.status() == BackgroundJobs.Status.RUNNING)
                                    .count();
                    jobsButton.setText(
                            running == 0
                                    ? "Background jobs"
                                    : "Background jobs (" + running + " running)");
                    jobsButton.setToolTipText(
                            jobs.isEmpty() ? "No background jobs" : "View background job status");
                };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(update);
        }
    }

    private void showJobs() {
        final JPopupMenu menu = new JPopupMenu();
        if (jobs.isEmpty()) {
            menu.add("No background jobs").setEnabled(false);
        } else {
            jobs.stream()
                    .sorted(java.util.Comparator.comparing(BackgroundJobs.Job::title))
                    .forEach(
                            job ->
                                    menu.add(
                                            job.title()
                                                    + " - "
                                                    + job.status()
                                                    + ": "
                                                    + job.message()));
        }
        menu.show(jobsButton, 0, -menu.getPreferredSize().height);
    }
}
