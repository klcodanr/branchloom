package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.PullRequestInfo;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Shared presentation formatting for pull-request status values. */
public final class GitFormatter {
    private static final String UNAVAILABLE = "Unavailable";

    private GitFormatter() {}

    public static String detailsHtml(final PullRequestInfo request) {
        final String lifecycle = request.draft() ? "Draft" : UiText.titleCase(request.state());
        return "<html><b>#"
                + request.number()
                + "</b>  "
                + UiText.escapeHtml(request.title())
                + "<br>"
                + "<font color='"
                + UiText.colorHex(UIManager.getColor(UiConstants.DISABLED_FOREGROUND))
                + "'>"
                + lifecycle
                + "  ·  "
                + reviewStatus(request.reviewDecision())
                + "  ·  "
                + mergeStatus(request.mergeState())
                + "  ·  "
                + checksSummary(
                        request.checksPassed(), request.checksTotal(), request.checksStatus())
                + "</font>"
                + "</html>";
    }

    public static String statusHtml(final PullRequestInfo request) {
        final String color =
                UiText.colorHex(
                        UiText.pullRequestIndicatorColor(
                                request.mergeState(), request.checksStatus()));
        return "PR: <font color='"
                + color
                + "'>&#9679;</font> "
                + mergeStatus(request.mergeState())
                + "  ·  Checks: "
                + request.checksPassed()
                + "/"
                + request.checksTotal()
                + " "
                + UiText.titleCase(request.checksStatus());
    }

    public static String mergeStatus(final String value) {
        if ("CLEAN".equals(value) || "MERGEABLE".equals(value)) {
            return "Can merge";
        }
        if ("CONFLICTING".equals(value)) {
            return "Cannot merge";
        }
        if ("QUEUED".equals(value)) {
            return "In merge queue";
        }
        return "Mergeability unknown";
    }

    public static String checksSummary(final int passed, final int total, final String status) {
        return passed + "/" + total + " checks " + UiText.titleCase(status);
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
                constraints.insets = new Insets(0, 0, 0, 12);
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
                constraints.insets = new Insets(0, 0, 0, 0);
                change.add(UiFactory.label(fields[2], Theme.FontSize.SM), constraints);
                diff.add(change);
            }
        }
        diff.revalidate();
        diff.repaint();
    }

    private static javax.swing.JTextArea value(final String text) {
        final var area = UiFactory.selectableText(text, Theme.FontSize.MD);
        area.setAlignmentX(javax.swing.JComponent.LEFT_ALIGNMENT);
        return area;
    }

    private static String reviewStatus(final String reviewDecision) {
        return reviewDecision.isBlank()
                ? "Review pending"
                : "Review: " + UiText.titleCase(reviewDecision);
    }
}
