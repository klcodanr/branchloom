package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.components.SessionActions;
import com.jagent.desktop.ui.components.StatusDot;
import com.jagent.desktop.ui.components.UiText;
import javax.swing.JPopupMenu;

public class SessionTreeItem implements TreeItem {
    private final ActionContext actionContext;
    private final SessionId sessionId;

    public SessionTreeItem(final ActionContext actionContext, final SessionId sessionId) {
        this.actionContext = actionContext;
        this.sessionId = sessionId;
    }

    @Override
    public String getId() {
        return sessionId.toString();
    }

    public SessionId getSessionId() {
        return sessionId;
    }

    @Override
    public String getTooltip() {
        final Session session = actionContext.appState().sessions().get(sessionId);
        final String agent = session.agent();
        final String promptValue = session.prompt();
        final String worktreePath = session.worktreePath();
        String prompt = UiText.valueOrDefault(promptValue, "").trim();
        if (prompt.length() > 140) {
            prompt = prompt.substring(0, 137) + "...";
        }
        final PullRequestDetails pullRequest =
                actionContext.appState().monitoringStatuses().get(sessionId);
        final String pullRequestHtml =
                pullRequest == null
                        ? ""
                        : "<br>PR: <font color='"
                                + UiText.colorHex(pullRequest.indicatorColor())
                                + "'>&#9679;</font> "
                                + UiText.titleCase(pullRequest.status().toString());
        return "<html><b>"
                + UiText.escapeHtml(session.name())
                + "</b>"
                + (agent == null || agent.isBlank() ? "" : "<br>Agent: " + UiText.escapeHtml(agent))
                + (worktreePath == null || worktreePath.isBlank()
                        ? ""
                        : "<br>Worktree: " + UiText.escapeHtml(worktreePath))
                + (prompt.isBlank() ? "" : "<br><br>" + UiText.escapeHtml(prompt))
                + pullRequestHtml
                + "</html>";
    }

    @Override
    public CellRenderContents getCellRenderContents() {
        final Session session = actionContext.appState().sessions().get(sessionId);
        final var terminals =
                session.terminalIds().stream()
                        .map(TerminalManager.get()::state)
                        .filter(state -> state != null)
                        .toList();
        final var terminalState =
                terminals.stream()
                        .filter(state -> state == TerminalState.FAILED)
                        .findFirst()
                        .orElseGet(() -> terminals.stream().findFirst().orElse(null));
        return new CellRenderContents(session.name(), StatusDot.terminalIcon(terminalState));
    }

    @Override
    public JPopupMenu getMenu() {
        final Session session = actionContext.appState().sessions().get(sessionId);
        actionContext.viewController().selectSession(session.projectId(), sessionId);
        return SessionActions.menu(actionContext, sessionId);
    }

    @Override
    public void select() {
        final Session session = actionContext.appState().sessions().get(sessionId);
        actionContext.viewController().openSession(session.projectId(), sessionId);
    }
}
