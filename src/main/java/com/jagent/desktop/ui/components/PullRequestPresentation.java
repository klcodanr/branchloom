package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.ui.utils.RelativeTime;
import java.awt.Color;
import java.util.Date;
import java.util.Locale;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

final class PullRequestPresentation {
    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

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

    private static String wrapMessage(final String message) {
        return wrapHtml("<p style='margin:0;'>" + message + "</p>");
    }

    private static String wrapHtml(final String content) {
        final java.awt.Font font = Theme.font(Theme.FontSize.SM);
        return "<html><body style='font-family:"
                + escapeHtml(font.getFamily())
                + "; font-size:"
                + font.getSize()
                + "px; margin:0;'>"
                + content
                + "</body></html>";
    }

    public static String bodyHtml(final String value) {
        if (value == null || value.isBlank()) {
            return wrapMessage("No description provided.");
        }
        return wrapHtml(RENDERER.render(PARSER.parse(UiText.valueOrDefault(value, ""))));
    }

    public static String loadingBodyHtml() {
        return wrapMessage("Loading description...");
    }

    public static String errorBodyHtml(final Throwable failure) {
        final Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return wrapMessage("Description could not be rendered: " + escapeHtml(cause.getMessage()));
    }

    public static String changesHtml(final PullRequestDetails details) {
        if (details == null) {
            return "Loading...";
        }
        final Color additionsColor =
                Theme.Colors.success() == null ? Theme.Colors.foreground() : Theme.Colors.success();
        final Color deletionsColor =
                Theme.Colors.danger() == null ? Theme.Colors.foreground() : Theme.Colors.danger();
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
        return Theme.Colors.foreground();
    }

    public static Color selectionColor() {
        final Color color = Theme.Colors.textSelectionBackground();
        return color == null ? Theme.Colors.foreground() : color;
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

    public static boolean contains(final String value, final String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
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
