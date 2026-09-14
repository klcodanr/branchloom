package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Shows the full context and output for a background job. */
public final class BackgroundJobDialog {
    private BackgroundJobDialog() {}

    public static void show(final Component parent, final BackgroundJobs.Job job) {
        final Window owner = SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog =
                new JDialog(owner, job.title(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content(job, dialog));
        dialog.pack();
        dialog.setMinimumSize(new Dimension(520, 360));
        dialog.setLocationRelativeTo(owner == null ? parent : owner);
        dialog.setVisible(true);
    }

    /* package */ static JPanel content(final BackgroundJobs.Job job, final JDialog dialog) {
        final JPanel content = new JPanel(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        content.setBorder(
                BorderFactory.createEmptyBorder(
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING,
                        UiConstants.CONTENT_PADDING));

        final JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        final JLabel title = leftLabel(job.title(), Theme.FontSize.XL);
        title.setFont(Theme.boldFont(Theme.FontSize.XL));
        final JLabel status = leftLabel(statusText(job.status()), Theme.FontSize.SM);
        status.setForeground(statusColor(job.status()));
        heading.add(title);
        heading.add(status);
        content.add(heading, BorderLayout.NORTH);

        final JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        if (!job.project().isBlank()) {
            details.add(leftLabel("Project: " + job.project(), Theme.FontSize.SM));
        }
        if (!job.session().isBlank()) {
            details.add(leftLabel("Session: " + job.session(), Theme.FontSize.SM));
        }
        details.add(Box.createVerticalStrut(UiConstants.SPACING_SM));
        final JLabel activityTitle = leftLabel("Current activity", Theme.FontSize.SM);
        activityTitle.setFont(Theme.boldFont(Theme.FontSize.SM));
        details.add(activityTitle);
        details.add(leftLabel(job.message(), Theme.FontSize.MD));
        details.add(Box.createVerticalStrut(UiConstants.SPACING_SM));
        final JLabel outputTitle = leftLabel("Console output", Theme.FontSize.SM);
        outputTitle.setFont(Theme.boldFont(Theme.FontSize.SM));
        details.add(outputTitle);
        final var output = UiFactory.selectableText(job.output(), Theme.FontSize.SM);
        output.setRows(Math.min(8, Math.max(3, job.output().split("\\R").length)));
        output.setColumns(52);
        output.setCaretPosition(0);
        final JScrollPane outputScroll = new JScrollPane(output);
        outputScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(outputScroll);
        content.add(details, BorderLayout.CENTER);

        final JButton close = UiFactory.button("Close");
        close.addActionListener(
                event -> {
                    if (dialog != null) {
                        dialog.dispose();
                    }
                });
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(close);
        content.add(footer, BorderLayout.SOUTH);
        return content;
    }

    private static JLabel leftLabel(final String text, final Theme.FontSize size) {
        final JLabel label = UiFactory.label(text, size);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static String statusText(final BackgroundJobs.Status status) {
        return switch (status) {
            case RUNNING -> "Running";
            case SUCCEEDED -> "Completed";
            case FAILED -> "Failed";
        };
    }

    private static Color statusColor(final BackgroundJobs.Status status) {
        return switch (status) {
            case RUNNING -> Theme.warningColor();
            case SUCCEEDED -> Theme.successColor();
            case FAILED -> Theme.dangerColor();
        };
    }
}
