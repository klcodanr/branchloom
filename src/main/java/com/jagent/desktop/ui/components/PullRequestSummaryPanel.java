package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.ui.utils.RelativeTime;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class PullRequestSummaryPanel extends JPanel {
    private static final String LABEL_FOREGROUND = "Label.foreground";
    private final PullRequestChecksPanel checks = new PullRequestChecksPanel();

    public PullRequestSummaryPanel() {
        super(new BorderLayout(0, UiConstants.SPACING_MD));
        setOpaque(false);
        setBorder(
                new EmptyBorder(
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS));
    }

    public void render(final PullRequest request) {
        removeAll();
        if (request == null) {
            final JLabel empty =
                    UiFactory.label("Select a pull request to see details.", Theme.FontSize.MD);
            empty.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
            add(empty, BorderLayout.NORTH);
            return;
        }

        final Instant now = Instant.now();
        final JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setAlignmentX(LEFT_ALIGNMENT);
        final JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_SM, 0));
        header.setAlignmentX(LEFT_ALIGNMENT);
        final JLabel number = UiFactory.label("#" + request.number(), Theme.FontSize.SM);
        number.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        header.add(number);
        final JButton title =
                UiFactory.link(request.title(), () -> PlatformCommands.openUrl(request.url()));
        title.setFont(Theme.boldFont(Theme.FontSize.XL));
        header.add(title);
        top.add(header);
        top.add(Box.createVerticalStrut(UiConstants.SPACING_MD));

        final JPanel facts = new JPanel(new java.awt.GridLayout(0, 1, 0, UiConstants.SPACING_XS));
        facts.setOpaque(false);
        facts.setAlignmentX(LEFT_ALIGNMENT);
        facts.add(
                factsLine(
                        iconLinkValue(
                                UiIcons.userRoundArrowLeft(),
                                "@" + request.author(),
                                "Author",
                                authorUrl(request)),
                        iconLinkValue(
                                UiIcons.gitBranch(),
                                blankAsNone(request.headBranch()),
                                "Branch",
                                request.url())));
        facts.add(
                factsLine(
                        iconValue(
                                UiIcons.messageSquareDiff(),
                                formatReview(request),
                                "Review status"),
                        iconValue(
                                UiIcons.gitCompareArrows(),
                                GitFormatter.mergeStatus(request.mergeable()),
                                "Merge status",
                                mergeStatusColor(request)),
                        iconValue(
                                UiIcons.activity(),
                                request.checksPassed()
                                        + "/"
                                        + request.checksTotal()
                                        + " "
                                        + UiText.titleCase(request.checksStatus()),
                                "Checks")));
        facts.add(
                factsLine(
                        iconValue(
                                UiIcons.gitCompare(),
                                changesHtml(request),
                                "Changes",
                                UIManager.getColor(LABEL_FOREGROUND),
                                changesTooltip(request)),
                        iconValue(
                                UiIcons.pullRequestCreate(),
                                offsetOnly(request.createdAt(), now),
                                "Opened"),
                        iconValue(
                                UiIcons.rotateCwClock(),
                                offsetOnly(request.updatedAt(), now),
                                "Updated")));
        top.add(facts);
        add(top, BorderLayout.NORTH);

        final JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        checks.render(request);
        checks.setAlignmentX(LEFT_ALIGNMENT);
        center.add(checks);
        center.add(Box.createVerticalStrut(UiConstants.SPACING_SM));

        final JPanel bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.setOpaque(false);
        bodyPanel.setAlignmentX(LEFT_ALIGNMENT);
        bodyPanel.setBorder(UiFactory.contentAreaBorder());
        final JEditorPane body = new JEditorPane("text/html", bodyHtml(request.description()));
        body.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        body.setFont(Theme.font(Theme.FontSize.SM));
        body.setEditable(false);
        body.setOpaque(false);
        body.setFocusable(false);
        body.setBorder(new EmptyBorder(0, 0, 0, 0));
        bodyPanel.add(body, BorderLayout.CENTER);
        center.add(bodyPanel);
        add(center, BorderLayout.CENTER);
    }

    private static JPanel iconValue(final Icon icon, final String value, final String tooltip) {
        return iconValue(icon, value, tooltip, UIManager.getColor(LABEL_FOREGROUND));
    }

    private static JPanel iconValue(
            final Icon icon, final String value, final String tooltip, final Color color) {
        return iconValue(icon, value, tooltip, color, value);
    }

    private static JPanel iconValue(
            final Icon icon,
            final String value,
            final String tooltip,
            final Color color,
            final String tooltipValue) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_XS, 0));
        row.setOpaque(false);
        row.setToolTipText(tooltip);
        final JLabel iconLabel = new JLabel(icon);
        iconLabel.setToolTipText(tooltip);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        row.add(iconLabel);
        final JLabel text = UiFactory.label(value, Theme.FontSize.SM);
        text.setToolTipText(tooltip + ": " + tooltipValue);
        if (color != null) {
            text.setForeground(color);
        }
        row.add(text);
        return row;
    }

    private static JPanel iconLinkValue(
            final Icon icon, final String value, final String tooltip, final String url) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_XS, 0));
        row.setOpaque(false);
        row.setToolTipText(tooltip);
        final JLabel iconLabel = new JLabel(icon);
        iconLabel.setToolTipText(tooltip);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        row.add(iconLabel);
        final JButton link = UiFactory.link(value, () -> PlatformCommands.openUrl(url));
        link.setToolTipText(tooltip + ": " + value);
        row.add(link);
        return row;
    }

    private static JPanel factsLine(final JComponent... items) {
        final JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_MD, 0));
        line.setOpaque(false);
        for (final JComponent item : items) {
            line.add(item);
        }
        return line;
    }

    private static String formatReview(final PullRequest request) {
        if (request.draft()) {
            return "Draft";
        }
        final String value = request.reviewDecision();
        return UiText.titleCase(value == null ? "unknown" : value.replace('_', ' '));
    }

    private static String bodyHtml(final String value) {
        if (value == null || value.isBlank()) {
            return "<html><body style='font-family:sans-serif; font-size:12px; margin:0;'>"
                    + "<p style='margin:0;'>No description provided.</p></body></html>";
        }
        final String content = value.replace("\\r\\n", "\n").replace("\\n", "\n");
        if (content.toLowerCase(Locale.ROOT).contains("<html")
                || content.toLowerCase(Locale.ROOT).contains("<body")
                || content.toLowerCase(Locale.ROOT).contains("<p")
                || content.toLowerCase(Locale.ROOT).contains("<div")) {
            return content;
        }
        return "<html><body style='font-family:sans-serif; font-size:12px; margin:0;'>"
                + content
                + "</body></html>";
    }

    private static String blankAsNone(final String value) {
        return value == null || value.isBlank() ? "None" : value;
    }

    private static String changesHtml(final PullRequest request) {
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
                + request.additions()
                + "</font>  "
                + "<font color='"
                + UiText.colorHex(deletionsColor)
                + "'>-"
                + request.deletions()
                + "</font>"
                + "  files "
                + request.changedFiles()
                + "</html>";
    }

    private static String changesTooltip(final PullRequest request) {
        return "+"
                + request.additions()
                + "  -"
                + request.deletions()
                + " files "
                + request.changedFiles();
    }

    private static Color mergeStatusColor(final PullRequest request) {
        final Color color =
                UiText.pullRequestIndicatorColor(request.mergeable(), request.checksStatus());
        if (color != null) {
            return color;
        }
        final Color fallback = UIManager.getColor(LABEL_FOREGROUND);
        return fallback == null ? Color.GRAY : fallback;
    }

    private static String offsetOnly(final String timestamp, final Instant now) {
        final String offset = RelativeTime.offsetTime(timestamp, now);
        if ("unknown".equals(offset)) {
            return "Unknown";
        }
        return "now".equals(offset) ? "just now" : offset + " ago";
    }

    private static String authorUrl(final PullRequest request) {
        final String author = request.author() == null ? "" : request.author().trim();
        if (author.isEmpty()) {
            return request.url();
        }
        try {
            final URI uri = URI.create(request.url());
            final String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            final String host = uri.getHost() == null ? "github.com" : uri.getHost();
            return scheme + "://" + host + "/" + author;
        } catch (IllegalArgumentException exception) {
            return "https://github.com/" + author;
        }
    }
}
