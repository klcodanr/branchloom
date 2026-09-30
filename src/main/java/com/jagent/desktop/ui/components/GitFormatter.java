package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

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

    public static void renderDiff(final RSyntaxTextArea diff, final String output) {
        diff.setText(output.isBlank() ? "No changes from HEAD." : output);
        diff.setCaretPosition(0);
    }

    private static JTextArea value(final String text) {
        final var area = UiFactory.selectableText(text, Theme.FontSize.MD);
        area.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return area;
    }
}
