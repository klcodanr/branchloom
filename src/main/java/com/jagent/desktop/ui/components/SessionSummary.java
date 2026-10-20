package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.AsyncStateListener;
import com.jagent.desktop.async.SessionInstanceState;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.SessionSummaryInfo;
import com.jagent.desktop.services.AppState;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionSummary extends ScrollablePanel {
    public static final int LABEL_WIDTH = 80;
    private static final Logger LOG = LoggerFactory.getLogger(SessionSummary.class);

    private final transient SessionInstanceState sessionInstanceState;
    private final transient SessionId sessionId;
    private final transient Session session;
    private final SessionBranchPanel branch;
    private final PullRequestSummaryButton pullRequest;
    private final SessionDiffPanel diff;
    private final AgentContextEditor agentContext;
    private final Alert cleanupAlert;

    public SessionSummary(
            final SessionId sessionId,
            final AppState appState,
            final Runnable removeSessionAndWorktree,
            final SessionInstanceState sessionInstanceState) {
        super();
        this.sessionId = sessionId;
        this.sessionInstanceState = sessionInstanceState;
        this.session = appState.sessions().get(sessionId);

        agentContext = new AgentContextEditor(appState);
        branch = new SessionBranchPanel();
        pullRequest = new PullRequestSummaryButton();
        diff = new SessionDiffPanel();

        final Optional<SessionSummaryInfo> initialState =
                Optional.ofNullable(sessionInstanceState.get(sessionId)).orElseGet(Optional::empty);
        if (initialState.isPresent()) {
            final SessionSummaryInfo info = initialState.get();
            this.updateSessionInfo(info);
        } else {
            sessionInstanceState.fetch(sessionId, session);
        }
        sessionInstanceState.setListener(
                sessionId,
                new AsyncStateListener<>() {

                    @Override
                    public void onComplete(final SessionSummaryInfo data) {
                        SessionSummary.this.updateSessionInfo(data);
                    }

                    @Override
                    public void onFailure(final Throwable error) {
                        LOG.warn("Failed to update session summary info", error);
                    }
                });

        setBorder(UiBorders.section());
        setLayout(new BorderLayout(0, UiConstants.SPACING_XL));
        cleanupAlert =
                new Alert(
                        new Alert.Content(
                                "Ready for clean up! The pull request is finished and this "
                                        + "worktree has no uncommitted changes.",
                                Theme.Colors.success(),
                                "Remove session and worktree",
                                removeSessionAndWorktree));
        cleanupAlert.setVisible(false);
        add(details(), BorderLayout.CENTER);
        refresh();
    }

    private void updateSessionInfo(final SessionSummaryInfo info) {
        agentContext.setContent(info.agentContext());
        branch.setWorktreeStatus(info.worktreeStatus());
        diff.setWorktreeStatus(info.worktreeStatus());
        pullRequest.setPullRequest(info.pullRequest());
        cleanupAlert.setVisible(shouldShowCleanupAlert(info));
        revalidate();
    }

    public static boolean shouldShowCleanupAlert(final SessionSummaryInfo info) {
        return info != null
                && info.pullRequest() != null
                && info.pullRequest().state() == PullRequest.State.MERGED
                && info.worktreeStatus() != null
                && info.worktreeStatus().clean();
    }

    private JPanel details() {
        final FlexGrid details = new FlexGrid(UiConstants.SPACING_LG);

        details.add(cleanupAlert, 12);
        details.add(new JLabel("Pull Request"), 3);

        final JPanel buttonContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonContainer.add(pullRequest);
        details.add(buttonContainer, 9);
        details.add(new JLabel("Created"), 3);
        details.add(new SelectableTextLabel(UiText.relativeTime(session.created())), 9);
        details.add(new JLabel("Branch"), 3);
        details.add(branch, 9);
        details.add(new JLabel("Worktree"), 3);
        details.add(new SelectableTextLabel(session.worktreePath()), 9);
        details.add(new JLabel("Prompt"), 3);
        details.add(scrollablePrompt(), 9);
        details.add(new JLabel("Agent Context"), 3);
        details.add(agentContext, 9);
        details.add(new JLabel("Changes"), 3);
        details.add(diff, 9);

        return details;
    }

    private JScrollPane scrollablePrompt() {
        final JScrollPane scroll = new JScrollPane(new SelectableTextLabel(session.prompt()));
        scroll.setPreferredSize(new Dimension(0, 170));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public void refresh() {
        this.sessionInstanceState.fetch(this.sessionId, this.session);
    }
}
