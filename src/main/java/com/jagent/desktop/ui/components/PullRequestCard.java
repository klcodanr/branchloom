package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.actions.ImportBranchAction;
import com.jagent.desktop.ui.dialogs.ReviewDialog;
import java.awt.Dimension;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

/** Displays one pull request and its actions. */
public final class PullRequestCard extends JPanel {
    private static final Logger LOG = Logger.getLogger(PullRequestCard.class.getName());
    private final transient ActionContext actionContext;

    public PullRequestCard(final ActionContext actionContext, final PullRequest request) {
        super();
        this.actionContext = actionContext;
        final JPopupMenu contextMenu = menu(request);
        setBackground(UIManager.getColor("TextField.background"));
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                        UiFactory.cardBorder()));
        setPreferredSize(new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        setMinimumSize(new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, UiConstants.PR_CARD_HEIGHT));
        setAlignmentX(LEFT_ALIGNMENT);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        final JLabel number = UiFactory.label("#" + request.number(), Theme.FontSize.XS);
        number.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        number.setFont(Theme.boldFont(Theme.FontSize.XS));
        number.setAlignmentX(LEFT_ALIGNMENT);
        number.setComponentPopupMenu(contextMenu);
        add(number);
        final JButton title =
                UiFactory.link(request.title(), () -> PlatformCommands.openUrl(request.url()));
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setToolTipText(request.title());
        title.getAccessibleContext().setAccessibleName("Open pull request: " + request.title());
        title.setFont(Theme.boldFont(Theme.FontSize.MD));
        title.setComponentPopupMenu(contextMenu);
        add(title);
        final JComponent statusDot =
                new StatusDot(UiText.checksColor(request.checksStatus()), checksSummary(request));
        final JPanel statusRow = new JPanel();
        statusRow.setOpaque(false);
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        statusRow.setAlignmentX(LEFT_ALIGNMENT);
        final JLabel metadata =
                UiFactory.label(
                        "@"
                                + request.author()
                                + "  "
                                + reviewStatus(request)
                                + "  ·  "
                                + mergeStatus(request),
                        Theme.FontSize.XS);
        metadata.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        metadata.setToolTipText(
                "@"
                        + request.author()
                        + "  "
                        + reviewStatus(request)
                        + "  ·  "
                        + mergeStatus(request));
        statusRow.add(statusDot);
        statusRow.add(Box.createHorizontalStrut(UiConstants.SPACING_XS));
        statusRow.add(metadata);
        statusRow.setComponentPopupMenu(contextMenu);
        add(statusRow);
        setComponentPopupMenu(contextMenu);
    }

    private JPopupMenu menu(final PullRequest request) {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem open = new JMenuItem("Open PR");
        open.addActionListener(event -> PlatformCommands.openUrl(request.url()));
        final JMenuItem importItem = new JMenuItem("Import PR branch");
        importItem.addActionListener(
                event -> ImportBranchAction.importPullRequest(actionContext, request));
        menu.add(open);
        menu.addSeparator();
        menu.add(importItem);
        final JMenuItem reviewItem = new JMenuItem("Review PR");
        reviewItem.addActionListener(event -> startReview(request));
        menu.add(reviewItem);
        final Project project =
                request.projectId() == null
                        ? null
                        : actionContext.appState().projects().get(request.projectId());
        if (project != null && authoredByConfiguredUser(project, request)) {
            menu.addSeparator();
            final JMenuItem draftItem =
                    new JMenuItem(request.draft() ? "Mark ready for review" : "Convert to draft");
            draftItem.addActionListener(event -> changeDraftState(project, request));
            menu.add(draftItem);
        }
        if (project != null && mergeable(request)) {
            final JMenuItem mergeItem = new JMenuItem("Merge PR");
            mergeItem.addActionListener(event -> merge(project, request));
            menu.add(mergeItem);
        }
        if (project != null) {
            final JMenuItem closeItem = new JMenuItem("Close PR");
            closeItem.addActionListener(event -> close(project, request));
            menu.add(closeItem);
        }
        return menu;
    }

    private void changeDraftState(final Project project, final PullRequest request) {
        final String action = request.draft() ? "mark ready for review" : "convert to draft";
        if (!confirm(request, action)) {
            return;
        }
        runGitHubAction(
                "PR " + action,
                () -> {
                    if (request.draft()) {
                        GitHub.markReady(project, request.number());
                    } else {
                        GitHub.convertToDraft(project, request.number());
                    }
                });
    }

    private void merge(final Project project, final PullRequest request) {
        if (!confirm(request, "merge")) {
            return;
        }
        runGitHubAction("Merge PR", () -> GitHub.merge(project, request.number()));
    }

    private void close(final Project project, final PullRequest request) {
        if (!confirm(request, "close")) {
            return;
        }
        runGitHubAction("Close PR", () -> GitHub.close(project, request.number()));
    }

    private void runGitHubAction(final String name, final ThrowingAction action) {
        BackgroundTasks.submit(
                        name,
                        "pull-request-action",
                        () -> {
                            try {
                                action.run();
                            } catch (IOException exception) {
                                throw new CompletionException(exception);
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new CompletionException(exception);
                            }
                        })
                .exceptionally(
                        failure -> {
                            LOG.warning(name + " failed: " + rootMessage(failure));
                            return null;
                        });
    }

    private boolean confirm(final PullRequest request, final String action) {
        return JOptionPane.showConfirmDialog(
                        actionContext.window(),
                        "Are you sure you want to " + action + " PR #" + request.number() + "?",
                        "Confirm " + action,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    private static boolean authoredByConfiguredUser(
            final Project project, final PullRequest request) {
        final String user = project.githubUser();
        return user != null && user.equalsIgnoreCase(request.author());
    }

    private static boolean mergeable(final PullRequest request) {
        return !request.draft()
                && !"CONFLICTING".equals(request.mergeable())
                && !"DIRTY".equals(request.mergeable())
                && !"BLOCKED".equals(request.mergeable())
                && !"FAILING".equals(request.checksStatus())
                && !"PENDING".equals(request.checksStatus());
    }

    private static String rootMessage(final Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws IOException, InterruptedException;
    }

    private void startReview(final PullRequest request) {
        if (actionContext.appState().appSettings().agents().isEmpty()) {
            LOG.warning("Review PR: No agents configured");
            final Object[] options = {"Open settings", "Cancel"};
            final int choice =
                    JOptionPane.showOptionDialog(
                            actionContext.window(),
                            "Configure an agent before starting a review.",
                            "No review agent configured",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            options,
                            options[0]);
            if (choice == 0) {
                actionContext
                        .viewCoordinator()
                        .updateView(ViewId.SETTINGS, ViewCoordinator.ViewState.reset());
            }
            return;
        }
        new ReviewDialog(
                        actionContext,
                        request,
                        (agent, prompt) -> {
                            final String title = agent.name + " review #" + request.number();
                            final String command =
                                    agent.newSessionCommand.replace(
                                            "{prompt}", PlatformCommands.shellQuote(prompt));
                            final var terminalId =
                                    actionContext
                                            .appState()
                                            .addTerminal(
                                                    new Terminal(
                                                            null,
                                                            request.projectId(),
                                                            title,
                                                            command));
                            actionContext
                                    .viewCoordinator()
                                    .updateView(
                                            ViewId.PROJECT,
                                            ViewCoordinator.ViewState.projectTerminal(
                                                    request.projectId(), terminalId));
                        })
                .setVisible(true);
    }

    private static String reviewStatus(final PullRequest request) {
        if ("CONFLICTING".equals(request.mergeable())) {
            return "Conflicting";
        }
        if ("APPROVED".equals(request.reviewDecision())) {
            return "Approved";
        }
        if ("CHANGES_REQUESTED".equals(request.reviewDecision())) {
            return "Changes requested";
        }
        if (request.draft()) {
            return "Draft";
        }
        return "Ready for review";
    }

    private static String mergeStatus(final PullRequest request) {
        return GitFormatter.mergeStatus(request.mergeState());
    }

    private static String checksSummary(final PullRequest request) {
        return request.checksPassed()
                + "/"
                + request.checksTotal()
                + " checks "
                + UiText.titleCase(request.checksStatus());
    }
}
