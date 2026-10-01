package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.services.PlatformCommands;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PullRequestSummaryPanel extends ScrollablePanel {
    private final PullRequestChecksPanel checks = new PullRequestChecksPanel();
    private final AtomicLong renderGeneration = new AtomicLong();
    private final transient Map<String, String> bodyByRequest = new HashMap<>();
    private final transient Set<String> loadingBodies = new HashSet<>();
    private transient PullRequest displayedRequest;

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

    public void render(final PullRequest request, final PullRequestDetails details) {
        final long generation = renderGeneration.incrementAndGet();
        displayedRequest = request;
        removeAll();
        if (request == null) {
            final JLabel empty =
                    UiFactory.label("Select a pull request to see details.", Theme.FontSize.MD);
            empty.setForeground(Theme.Colors.muted());
            add(empty, BorderLayout.NORTH);
            return;
        }

        add(top(request, details), BorderLayout.NORTH);

        final JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        checks.render(
                request,
                details == null ? new PullRequestChecks(List.of()) : details.checks(),
                details == null);
        checks.setAlignmentX(LEFT_ALIGNMENT);
        center.add(checks);
        center.add(Box.createVerticalStrut(UiConstants.SPACING_SM));

        final JPanel bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.setOpaque(false);
        bodyPanel.setAlignmentX(LEFT_ALIGNMENT);
        bodyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        bodyPanel.setBorder(UiFactory.contentAreaBorder());
        final String bodyKey = PullRequestPresentation.bodyKey(request);
        final String cachedBody = bodyByRequest.get(bodyKey);
        final JEditorPane body =
                cachedBody == null
                        ? createBodyEditor(PullRequestPresentation.loadingBodyHtml())
                        : createBodyEditor(cachedBody);
        bodyPanel.add(body, BorderLayout.CENTER);
        center.add(bodyPanel);
        add(center, BorderLayout.CENTER);
        loadBodyAsync(request, generation, bodyKey, bodyPanel, body);
    }

    private JPanel top(final PullRequest request, final PullRequestDetails details) {

        final JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setAlignmentX(LEFT_ALIGNMENT);

        final JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_SM, 0));
        header.setAlignmentX(LEFT_ALIGNMENT);

        final JLabel number = UiFactory.label("#" + request.number(), Theme.FontSize.SM);
        number.setForeground(Theme.Colors.muted());
        header.add(number);

        final JButton title =
                UiFactory.link(
                        request.title(),
                        () -> PlatformCommands.openUrl(request.url().toExternalForm()));
        title.setFont(Theme.boldFont(Theme.FontSize.XL));
        header.add(title);

        top.add(header);
        top.add(Box.createVerticalStrut(UiConstants.SPACING_MD));

        final JPanel facts = new JPanel(new GridLayout(0, 1, 0, UiConstants.SPACING_XS));
        facts.setOpaque(false);
        facts.setAlignmentX(LEFT_ALIGNMENT);
        facts.add(
                factsLine(
                        iconValue(
                                UiIcons.userRoundArrowLeft(),
                                "Author",
                                link(
                                        "@" + request.author().login(),
                                        "Author: " + "@" + request.author().login(),
                                        request.author().url().toExternalForm())),
                        iconValue(
                                UiIcons.gitBranch(),
                                "Branch",
                                smallLabel(
                                        "Branch",
                                        "Branch: "
                                                + UiText.valueOrDefault(
                                                        request.headBranch(), "None"),
                                        null))));
        facts.add(
                factsLine(
                        iconValue(
                                UiIcons.messageSquareDiff(),
                                formatReviewStatus(request, details),
                                smallLabel(
                                        "Review status",
                                        "Review Status: " + formatReviewStatus(request, details),
                                        null)),
                        iconValue(
                                UiIcons.gitCompareArrows(),
                                "Merge status",
                                smallLabel(
                                        details == null
                                                ? "Loading"
                                                : UiText.titleCase(details.status().toString()),
                                        "Merge Status: "
                                                + (details == null
                                                        ? "Loading"
                                                        : UiText.titleCase(
                                                                details.status().toString())),
                                        PullRequestPresentation.mergeStatusColor(details)))));
        facts.add(
                factsLine(
                        iconValue(
                                UiIcons.gitCompare(),
                                "Changes",
                                smallLabel(
                                        PullRequestPresentation.changesHtml(details),
                                        PullRequestPresentation.changesTooltip(details),
                                        null)),
                        iconValue(
                                UiIcons.pullRequestCreate(),
                                "Opened",
                                smallLabel(
                                        PullRequestPresentation.offsetOnly(request.createdAt()),
                                        "Opened: "
                                                + PullRequestPresentation.offsetOnly(
                                                        request.createdAt()),
                                        null)),
                        iconValue(
                                UiIcons.rotateCwClock(),
                                "Updated",
                                smallLabel(
                                        PullRequestPresentation.offsetOnly(request.updatedAt()),
                                        "Updated: "
                                                + PullRequestPresentation.offsetOnly(
                                                        request.updatedAt()),
                                        null))));
        top.add(facts);
        return top;
    }

    private void loadBodyAsync(
            final PullRequest request,
            final long generation,
            final String bodyKey,
            final JPanel bodyPanel,
            final JEditorPane fallbackBody) {
        if (bodyByRequest.containsKey(bodyKey) || loadingBodies.contains(bodyKey)) {
            return;
        }
        loadingBodies.add(bodyKey);
        BackgroundOperations.submit(
                        "Pull Requests",
                        "render-pr-description",
                        () -> PullRequestPresentation.bodyHtml(request.description()))
                .thenAcceptAsync(
                        document -> {
                            loadingBodies.remove(bodyKey);
                            bodyByRequest.put(bodyKey, document);
                            if (generation == renderGeneration.get()
                                    && request.equals(displayedRequest)) {
                                bodyPanel.remove(fallbackBody);
                                bodyPanel.add(createBodyEditor(document), BorderLayout.CENTER);
                                bodyPanel.revalidate();
                                bodyPanel.repaint();
                            }
                        },
                        SwingUtilities::invokeLater)
                .exceptionally(
                        failure -> {
                            SwingUtilities.invokeLater(
                                    () -> {
                                        loadingBodies.remove(bodyKey);
                                        if (generation == renderGeneration.get()
                                                && request.equals(displayedRequest)) {
                                            fallbackBody.setText(
                                                    PullRequestPresentation.errorBodyHtml(failure));
                                            fallbackBody.setCaretPosition(0);
                                        }
                                    });
                            return null;
                        });
    }

    private static JLabel smallLabel(
            @NotNull final String text,
            @NotNull final String tooltip,
            @Nullable final Color color) {
        final JLabel label = UiFactory.label(text, Theme.FontSize.SM);
        label.setForeground(Theme.Colors.foreground());
        if (tooltip != null) {
            label.setToolTipText(tooltip);
        }
        if (color != null) {
            label.setForeground(color);
        }
        return label;
    }

    private static JButton link(
            @NotNull final String value, @NotNull final String tooltip, @NotNull final String url) {
        final JButton link = UiFactory.link(value, () -> PlatformCommands.openUrl(url));
        link.setToolTipText(tooltip);
        return link;
    }

    private static JPanel iconValue(final Icon icon, final String tooltip, final JComponent value) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_XS, 0));
        row.setOpaque(false);
        row.setToolTipText(tooltip);
        final JLabel iconLabel = new JLabel(icon);
        iconLabel.setToolTipText(tooltip);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        row.add(iconLabel);
        row.add(value);
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

    private static String formatReviewStatus(
            final PullRequest request, final PullRequestDetails requestDetails) {
        if (requestDetails != null && requestDetails.draft()) {
            return "Draft";
        }
        return UiText.titleCase(request.state().name());
    }

    private static JEditorPane createBodyEditor(final String html) {
        final JEditorPane body = new JEditorPane("text/html", html);
        body.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        body.setFont(Theme.font(Theme.FontSize.SM));
        body.setEditable(false);
        body.setOpaque(false);
        body.setFocusable(true);
        body.setMinimumSize(new Dimension(0, 0));
        body.setBorder(new EmptyBorder(0, 0, 0, 0));
        body.setCaretPosition(0);
        return body;
    }
}
