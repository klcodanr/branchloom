package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.ui.utils.RelativeTime;
import java.awt.Color;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import javax.swing.UIManager;

final class PullRequestPresentation {
    private static final String LABEL_FOREGROUND = "Label.foreground";

    private PullRequestPresentation() {}

    public static String checksKey(final PullRequest request) {
        return request.projectId().value() + ":" + request.number() + ":" + request.headSha();
    }

    public static String bodyKey(final PullRequest request) {
        return request.projectId().value()
                + ":"
                + request.number()
                + ":"
                + request.updatedAt().getTime();
    }

    public static String bodyHtml(final String value) {
        if (value == null || value.isBlank()) {
            return bodyPrefix() + "<p style='margin:0;'>No description provided.</p></body></html>";
        }
        return bodyPrefix() + MarkdownRenderer.toHtml(value) + "</body></html>";
    }

    public static String loadingBodyHtml() {
        return bodyPrefix() + "<p style='margin:0;'>Loading description...</p></body></html>";
    }

    public static String errorBodyHtml(final Throwable failure) {
        final Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return bodyPrefix()
                + "<p style='margin:0;'>Description could not be rendered: "
                + escapeHtml(cause.getMessage())
                + "</p></body></html>";
    }

    public static String authorLogin(final PullRequest request) {
        return request.author().login() == null || request.author().login().isBlank()
                ? "unknown"
                : request.author().login();
    }

    public static String authorUrl(final PullRequest request) {
        final URL authorUrl = request.author().url();
        if (authorUrl != null) {
            return authorUrl.toExternalForm();
        }
        return request.url().toExternalForm();
    }

    public static String blankAsNone(final String value) {
        return UiText.valueOrDefault(value, "None");
    }

    public static String changesHtml(final PullRequestDetails details) {
        if (details == null) {
            return "Loading...";
        }
        final Color additionsColor =
                Theme.successColor() == null
                        ? UIManager.getColor(LABEL_FOREGROUND)
                        : Theme.successColor();
        final Color deletionsColor =
                Theme.dangerColor() == null
                        ? UIManager.getColor(LABEL_FOREGROUND)
                        : Theme.dangerColor();
        return "<html><font color='"
                + UiText.colorHex(additionsColor)
                + "'>+"
                + details.additions()
                + "</font>  "
                + "<font color='"
                + UiText.colorHex(deletionsColor)
                + "'>-"
                + details.deletions()
                + "</font>"
                + "  files "
                + details.changedFiles()
                + "</html>";
    }

    public static String changesTooltip(final PullRequestDetails details) {
        if (details == null) {
            return "Loading changes...";
        }
        return "+"
                + details.additions()
                + "  -"
                + details.deletions()
                + " files "
                + details.changedFiles();
    }

    public static Color mergeStatusColor(final PullRequestDetails details) {
        final Color color = details == null ? null : details.indicatorColor();
        if (color != null) {
            return color;
        }
        final Color fallback = UIManager.getColor(LABEL_FOREGROUND);
        return fallback == null ? Color.GRAY : fallback;
    }

    public static String offsetOnly(final Date timestamp) {
        final String offset = RelativeTime.offsetTime(timestamp);
        if ("unknown".equals(offset)) {
            return "Unknown";
        }
        return "now".equals(offset) ? "just now" : offset + " ago";
    }

    public static String detailKey(final PullRequest request) {
        if (request == null) {
            return "";
        }
        return request.projectId() + ":" + request.number();
    }

    public static Color selectionColor() {
        final Color color = UIManager.getColor("Component.focusColor");
        if (color != null) {
            return color;
        }
        final Color borderColor = UIManager.getColor("Component.borderColor");
        return borderColor == null ? Color.GRAY : borderColor;
    }

    public static boolean contains(final String value, final String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String bodyPrefix() {
        final java.awt.Font font = Theme.font(Theme.FontSize.SM);
        return "<html><body style='font-family:"
                + escapeHtml(font.getFamily())
                + "; font-size:"
                + font.getSize()
                + "px; margin:0;'>";
    }

    private static String escapeHtml(final String value) {
        return value == null
                ? "Unknown error"
                : value.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }
}
