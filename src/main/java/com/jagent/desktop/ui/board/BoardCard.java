package com.jagent.desktop.ui.board;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.board.AgentSessionBoardItem;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.models.board.GithubIssueBoardItem;
import com.jagent.desktop.models.board.JiraIssueBoardItem;
import com.jagent.desktop.ui.components.CardPanel;
import com.jagent.desktop.ui.components.StatusDot;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.UiText;
import java.util.Date;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class BoardCard extends CardPanel {
    private static final String SEPARATOR = " · ";

    public BoardCard(final BoardItem item) {
        super();
        addNumber(number(item), null);
        final JLabel title = addTitle(item.title(), null);
        title.setName("title");
        addMetadata(new StatusDot(statusColor(state(item))), metadataText(item), null);
        add(timestamps(item));
    }

    private static String number(final BoardItem item) {
        return switch (item) {
            case PullRequest request ->
                    request.project().name() + SEPARATOR + "PR · #" + request.number();
            case GithubIssueBoardItem issue ->
                    issue.project() + SEPARATOR + "Issue · #" + issue.number();
            case AgentSessionBoardItem session -> session.project() + SEPARATOR + "Session";
            case JiraIssueBoardItem jira -> "Jira" + SEPARATOR + jira.id();
            default -> item.source().name() + projectId(item);
        };
    }

    private static String metadataText(final BoardItem item) {
        return switch (item) {
            case PullRequest request -> "@" + request.author().login();
            case GithubIssueBoardItem issue -> issue.project() + SEPARATOR + issue.state();
            case AgentSessionBoardItem session -> session.agent() + SEPARATOR + session.branch();
            case JiraIssueBoardItem jira ->
                    jira.author() + SEPARATOR + jira.assignee() + SEPARATOR + jira.status();
            default -> item.source().name() + projectId(item);
        };
    }

    private static String projectId(final BoardItem item) {
        if (item instanceof com.jagent.desktop.models.board.BasicBoardItem basic) {
            final String boardProjectId = basic.boardProjectId();
            if (boardProjectId != null && !boardProjectId.isBlank()) {
                return SEPARATOR + boardProjectId;
            }
        }
        return "";
    }

    private static JPanel timestamps(final BoardItem item) {
        final JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        addTimestamp(row, UiIcons.pullRequestCreate(), "Created", item.createdAt());
        row.add(Box.createHorizontalStrut(UiConstants.SPACING_SM));
        addTimestamp(row, UiIcons.rotateCwClock(), "Updated", item.updatedAt());
        return row;
    }

    private static void addTimestamp(
            final JPanel row,
            final javax.swing.Icon icon,
            final String label,
            final Date timestamp) {
        final String offset = UiText.relativeTime(timestamp);
        final JLabel value = new JLabel(offset, icon, JLabel.LEFT);
        value.setFont(Theme.font(Theme.FontSize.XS));
        value.setForeground(Theme.Colors.muted());
        final String description = "Unknown".equals(offset) ? label : label + " " + offset;
        final String localDateTime = UiText.localDateTime(timestamp);
        value.setToolTipText(timestamp == null ? description : description + "\n" + localDateTime);
        row.add(value);
    }

    private static String state(final BoardItem item) {
        return switch (item) {
            case PullRequest request -> request.state().name();
            case GithubIssueBoardItem issue -> issue.state();
            case AgentSessionBoardItem session -> session.state();
            case JiraIssueBoardItem jira -> jira.status();
            default -> null;
        };
    }

    private static java.awt.Color statusColor(final String state) {
        if ("closed".equalsIgnoreCase(state) || "merged".equalsIgnoreCase(state)) {
            return Theme.Colors.success();
        }
        if ("blocked".equalsIgnoreCase(state) || "failed".equalsIgnoreCase(state)) {
            return Theme.Colors.danger();
        }
        return Theme.Colors.focus();
    }
}
