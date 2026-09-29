package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.services.Git;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

/** Shared presentation formatting for pull-request status values. */
public final class GitFormatter {
    private static final String UNAVAILABLE = "Unavailable";

    private GitFormatter() {}

    public static String detailsHtml(final PullRequest request, final PullRequestDetails details) {
        final String lifecycle =
                details != null && details.draft()
                        ? "Draft"
                        : UiText.titleCase(request.state().toString());
        return "<html><b>#"
                + request.number()
                + "</b>  "
                + UiText.escapeHtml(request.title())
                + "<br>"
                + "<font color='"
                + UiText.colorHex(UIManager.getColor(UiConstants.DISABLED_FOREGROUND))
                + "'>"
                + lifecycle
                + "</font>"
                + "</html>";
    }

    public static String statusHtml(final PullRequestDetails details) {
        final String status =
                details == null ? "Loading" : UiText.titleCase(details.status().toString());
        final String color =
                UiText.colorHex(details == null ? Theme.mutedColor() : details.indicatorColor());
        return "PR: <font color='" + color + "'>&#9679;</font> " + status;
    }

    public static String checksSummary(final PullRequestChecks checks) {
        return checks.passed()
                + "/"
                + checks.total()
                + " checks "
                + UiText.titleCase(checks.checksStatus().toString());
    }

    public static String statusSummary(final Git.WorktreeStatus status) {
        final StringBuilder summary = new StringBuilder();
        appendStatus(summary, '+', status.additions());
        appendStatus(summary, '~', status.modifications());
        appendStatus(summary, '-', status.deletions());
        return summary.isEmpty() ? "Clean" : summary.toString();
    }

    public static void renderDiff(final JPanel diff, final String output) {
        diff.removeAll();
        if (output.isBlank()) {
            diff.add(value("No changes in worktree"));
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
                final GridBagConstraints constraints = new GridBagConstraints();
                constraints.anchor = GridBagConstraints.WEST;
                constraints.insets = new Insets(0, 0, 0, UiConstants.COMPONENT_GAP);
                constraints.gridx = 0;
                final JLabel additions = UiFactory.label("+" + fields[0], Theme.FontSize.XS);
                additions.setForeground(Theme.successColor());
                change.add(additions, constraints);
                constraints.gridx = 1;
                final JLabel deletions = UiFactory.label("-" + fields[1], Theme.FontSize.XS);
                deletions.setForeground(Theme.dangerColor());
                change.add(deletions, constraints);
                constraints.gridx = 2;
                constraints.weightx = 1;
                constraints.insets = UiConstants.ZERO_INSETS;
                change.add(UiFactory.label(fields[2], Theme.FontSize.SM), constraints);
                diff.add(change);
            }
        }
        diff.revalidate();
        diff.repaint();
    }

    public static void renderDiff(final JTextArea diff, final String output) {
        diff.setText(output.isBlank() ? "No changes from HEAD." : output);
        diff.getHighlighter().removeAllHighlights();
        int offset = 0;
        for (final String line : diff.getText().split("\\R", -1)) {
            highlightDiffLine(diff, line, offset);
            offset += line.length() + 1;
        }
        diff.setCaretPosition(0);
    }

    private static void highlightDiffLine(
            final JTextArea diff, final String line, final int offset) {
        final Color color = diffLineColor(line);
        if (color == null || line.isEmpty()) {
            return;
        }
        try {
            diff.getHighlighter()
                    .addHighlight(
                            offset,
                            offset + line.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(color));
        } catch (BadLocationException ignored) {
            // The text area can be updated while an asynchronous file load is completing.
        }
    }

    private static Color diffLineColor(final String line) {
        if (line.startsWith("+++") || line.startsWith("---")) {
            return null;
        }
        if (line.startsWith("+")) {
            return highlightColor(Theme.successColor(), new Color(46, 125, 50));
        }
        if (line.startsWith("-")) {
            return highlightColor(Theme.dangerColor(), new Color(198, 40, 40));
        }
        if (line.startsWith("@@")) {
            return highlightColor(Theme.warningColor(), new Color(173, 80, 0));
        }
        return null;
    }

    private static Color highlightColor(final Color color, final Color fallback) {
        final Color value = color == null ? fallback : color;
        return new Color(value.getRGB() & 0x00FFFFFF | 0x30000000, true);
    }

    private static JTextArea value(final String text) {
        final var area = UiFactory.selectableText(text, Theme.FontSize.MD);
        area.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return area;
    }

    private static void appendStatus(
            final StringBuilder summary, final char marker, final int count) {
        if (count > 0) {
            if (!summary.isEmpty()) {
                summary.append(' ');
            }
            summary.append(marker).append(count);
        }
    }
}
