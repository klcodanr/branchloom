package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.GitHubConnection;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.PlatformCommands;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.StringReader;
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
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PullRequestSummaryPanel extends JPanel {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestSummaryPanel.class);
    private static final String LABEL_FOREGROUND = "Label.foreground";
    private final PullRequestChecksPanel checks = new PullRequestChecksPanel();
    private final AtomicLong renderGeneration = new AtomicLong();
    private final transient Set<String> loadingChecks = new HashSet<>();
    private final transient Map<String, PullRequestChecks> checksByRequest = new HashMap<>();
    private final transient Map<String, HTMLDocument> bodyByRequest = new HashMap<>();
    private final transient Set<String> loadingBodies = new HashSet<>();
    private transient PullRequest displayedRequest;
    private transient Map<String, GitHubConnection> configuredConnections = Map.of();

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
            empty.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
            add(empty, BorderLayout.NORTH);
            return;
        }

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
                UiFactory.link(
                        request.title(),
                        () -> PlatformCommands.openUrl(request.url().toExternalForm()));
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
                                "@" + PullRequestPresentation.authorLogin(request),
                                "Author",
                                PullRequestPresentation.authorUrl(request)),
                        iconLinkValue(
                                UiIcons.gitBranch(),
                                PullRequestPresentation.blankAsNone(request.headBranch()),
                                "Branch",
                                request.url().toExternalForm())));
        facts.add(
                factsLine(
                        iconValue(
                                UiIcons.messageSquareDiff(),
                                formatReviewStatus(request, details),
                                "Review status"),
                        iconValue(
                                UiIcons.gitCompareArrows(),
                                details == null
                                        ? "Loading"
                                        : UiText.titleCase(details.status().toString()),
                                "Merge status",
                                PullRequestPresentation.mergeStatusColor(details))));
        facts.add(
                factsLine(
                        iconValue(
                                UiIcons.gitCompare(),
                                PullRequestPresentation.changesHtml(details),
                                "Changes",
                                null,
                                PullRequestPresentation.changesTooltip(details)),
                        iconValue(
                                UiIcons.pullRequestCreate(),
                                PullRequestPresentation.offsetOnly(request.createdAt()),
                                "Opened"),
                        iconValue(
                                UiIcons.rotateCwClock(),
                                PullRequestPresentation.offsetOnly(request.updatedAt()),
                                "Updated")));
        top.add(facts);
        add(top, BorderLayout.NORTH);

        final JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        final String checksKey = PullRequestPresentation.checksKey(request);
        final boolean checksLoading = !checksByRequest.containsKey(checksKey);
        checks.render(
                request,
                checksByRequest.getOrDefault(checksKey, new PullRequestChecks(List.of())),
                checksLoading);
        checks.setAlignmentX(LEFT_ALIGNMENT);
        center.add(checks);
        center.add(Box.createVerticalStrut(UiConstants.SPACING_SM));

        final JPanel bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.setOpaque(false);
        bodyPanel.setAlignmentX(LEFT_ALIGNMENT);
        bodyPanel.setBorder(UiFactory.contentAreaBorder());
        final String bodyKey = PullRequestPresentation.bodyKey(request);
        final HTMLDocument cachedBody = bodyByRequest.get(bodyKey);
        final JEditorPane body =
                cachedBody == null
                        ? createBodyEditor(PullRequestPresentation.loadingBodyHtml())
                        : createBodyEditor(cachedBody);
        bodyPanel.add(body, BorderLayout.CENTER);
        center.add(bodyPanel);
        add(center, BorderLayout.CENTER);
        loadBodyAsync(request, generation, bodyKey, bodyPanel, body);
        loadChecksAsync(request, generation, checksKey);
    }

    public void setConfiguredConnections(
            final Map<String, GitHubConnection> configuredConnections) {
        this.configuredConnections =
                configuredConnections == null ? Map.of() : Map.copyOf(configuredConnections);
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
        BackgroundTasks.submit(
                        "Pull Requests",
                        "render-pr-description",
                        () -> parseBody(PullRequestPresentation.bodyHtml(request.description())))
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

    private static String formatReviewStatus(
            final PullRequest request, final PullRequestDetails requestDetails) {
        if (requestDetails != null && requestDetails.draft()) {
            return "Draft";
        }
        return UiText.titleCase(request.state().name());
    }

    private void loadChecksAsync(
            final PullRequest request, final long generation, final String checksKey) {
        if (checksByRequest.containsKey(checksKey) || loadingChecks.contains(checksKey)) {
            return;
        }
        loadingChecks.add(checksKey);
        BackgroundTasks.submit("Pull Requests", "load-pr-checks", () -> loadChecks(request))
                .thenAcceptAsync(
                        pullRequestChecks -> {
                            loadingChecks.remove(checksKey);
                            checksByRequest.put(checksKey, pullRequestChecks);
                            if (generation == renderGeneration.get()
                                    && request.equals(displayedRequest)) {
                                checks.render(request, pullRequestChecks, false);
                                checks.revalidate();
                                checks.repaint();
                            }
                        },
                        SwingUtilities::invokeLater)
                .exceptionally(
                        failure -> {
                            SwingUtilities.invokeLater(() -> loadingChecks.remove(checksKey));
                            LOG.warn(
                                    "Failed to load checks for pull request: {}",
                                    request.number(),
                                    failure);
                            return null;
                        });
    }

    private PullRequestChecks loadChecks(final PullRequest request) {
        try {
            return GitHub.getChecks(request, configuredConnections);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load pull request checks", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Pull request checks loading was interrupted", exception);
        }
    }

    private static JEditorPane createBodyEditor(final String html) {
        final JEditorPane body = new JEditorPane("text/html", html);
        configureBodyEditor(body);
        body.setCaretPosition(0);
        return body;
    }

    private static JEditorPane createBodyEditor(final HTMLDocument document) {
        final JEditorPane body = new JEditorPane();
        body.setEditorKit(new HTMLEditorKit());
        body.setDocument(document);
        configureBodyEditor(body);
        body.setCaretPosition(0);
        return body;
    }

    private static void configureBodyEditor(final JEditorPane body) {
        body.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        body.setFont(Theme.font(Theme.FontSize.SM));
        body.setEditable(false);
        body.setOpaque(false);
        body.setFocusable(true);
        body.setBorder(new EmptyBorder(0, 0, 0, 0));
    }

    private static HTMLDocument parseBody(final String html) {
        final HTMLEditorKit kit = new HTMLEditorKit();
        final HTMLDocument document = (HTMLDocument) kit.createDefaultDocument();
        try {
            kit.read(new StringReader(html), document, 0);
            return document;
        } catch (IOException | BadLocationException exception) {
            throw new IllegalStateException("Could not parse pull request description", exception);
        }
    }
}
