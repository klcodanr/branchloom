package com.jagent.desktop.ui.views;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.board.AgentSessionBoardItem;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.models.board.GithubIssueBoardItem;
import com.jagent.desktop.models.board.JiraIssueBoardItem;
import com.jagent.desktop.ui.board.KanbanBoard;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComboBox;

final class BoardPresentation {
    private static final String ALL = "All";

    private BoardPresentation() {}

    public static void updateFilterOptions(
            final ActionContext context,
            final List<BoardItem> items,
            final JComboBox<String> projectFilter,
            final JComboBox<String> groupFilter) {
        final String selectedProject = (String) projectFilter.getSelectedItem();
        final String selectedGroup = (String) groupFilter.getSelectedItem();
        replaceOptions(
                projectFilter,
                items.stream()
                        .map(item -> projectName(context, item))
                        .filter(name -> !name.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList(),
                selectedProject);
        replaceOptions(
                groupFilter,
                items.stream()
                        .map(item -> projectGroup(context, item))
                        .filter(group -> !group.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList(),
                selectedGroup);
    }

    public static void apply(
            final ActionContext context,
            final List<BoardItem> items,
            final KanbanBoard board,
            final JComboBox<String> sourceFilter,
            final JComboBox<String> projectFilter,
            final JComboBox<String> groupFilter,
            final JComboBox<String> sortOrder,
            final String searchText) {
        final String selectedSource = (String) sourceFilter.getSelectedItem();
        final String selectedProject = (String) projectFilter.getSelectedItem();
        final String selectedGroup = (String) groupFilter.getSelectedItem();
        board.setItems(
                items.stream()
                        .filter(
                                item ->
                                        ALL.equals(selectedSource)
                                                || BoardView.sourceLabel(item.source())
                                                        .equals(selectedSource))
                        .filter(
                                item ->
                                        ALL.equals(selectedProject)
                                                || projectName(context, item)
                                                        .equals(selectedProject))
                        .filter(
                                item ->
                                        ALL.equals(selectedGroup)
                                                || projectGroup(context, item)
                                                        .equals(selectedGroup))
                        .filter(item -> matchesSearch(item, searchText))
                        .sorted(sortComparator(sortOrder))
                        .toList());
    }

    private static void replaceOptions(
            final JComboBox<String> comboBox, final List<String> options, final String selected) {
        comboBox.removeAllItems();
        comboBox.addItem(ALL);
        options.forEach(comboBox::addItem);
        comboBox.setSelectedItem(options.contains(selected) ? selected : ALL);
    }

    private static Comparator<BoardItem> sortComparator(final JComboBox<String> sortOrder) {
        final String selected = (String) sortOrder.getSelectedItem();
        if ("Created (newest first)".equals(selected)) {
            return Comparator.comparing(BoardItem::createdAt).reversed();
        }
        if ("Created (oldest first)".equals(selected)) {
            return Comparator.comparing(BoardItem::createdAt);
        }
        if ("Name (A-Z)".equals(selected)) {
            return Comparator.comparing(BoardItem::title, String.CASE_INSENSITIVE_ORDER);
        }
        if ("Name (Z-A)".equals(selected)) {
            return Comparator.comparing(BoardItem::title, String.CASE_INSENSITIVE_ORDER).reversed();
        }
        if ("Updated (oldest first)".equals(selected)) {
            return Comparator.comparing(BoardItem::updatedAt);
        }
        return Comparator.comparing(BoardItem::updatedAt).reversed();
    }

    private static boolean matchesSearch(final BoardItem item, final String searchText) {
        final String query = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        return query.isEmpty()
                || searchableText(item).stream()
                        .filter(value -> value != null)
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .anyMatch(value -> value.contains(query));
    }

    private static List<String> searchableText(final BoardItem item) {
        final List<String> values = new java.util.ArrayList<>();
        values.add(item.source().name());
        values.add(item.id());
        values.add(item.title());
        values.add(item.description());
        switch (item) {
            case PullRequest request -> {
                values.add(request.project().name());
                values.add(request.author().login());
                values.add(request.headBranch());
                values.add(request.baseBranch());
            }
            case GithubIssueBoardItem issue -> {
                values.add(issue.project());
                values.add(issue.state());
                values.add(Integer.toString(issue.number()));
            }
            case AgentSessionBoardItem session -> {
                values.add(session.agent());
                values.add(session.project());
                values.add(session.branch());
                values.add(session.state());
            }
            case JiraIssueBoardItem jira -> {
                values.add(jira.boardProjectId());
                values.add(jira.status());
                values.add(jira.author());
                values.add(jira.assignee());
            }
            default -> {}
        }
        return values;
    }

    private static String projectName(final ActionContext context, final BoardItem item) {
        if (item instanceof PullRequest request) {
            return request.project().name();
        }
        if (item instanceof GithubIssueBoardItem issue) {
            return issue.project();
        }
        if (item instanceof AgentSessionBoardItem session) {
            return session.project();
        }
        return projectForId(context, item).map(com.jagent.desktop.models.Project::name).orElse("");
    }

    private static String projectGroup(final ActionContext context, final BoardItem item) {
        return projectForId(context, item)
                .map(value -> Objects.requireNonNullElse(value.group(), ""))
                .orElse("");
    }

    private static Optional<com.jagent.desktop.models.Project> projectForId(
            final ActionContext context, final BoardItem item) {
        final String id =
                switch (item) {
                    case PullRequest request -> request.projectId().value().toString();
                    case GithubIssueBoardItem issue -> issue.boardProjectId();
                    case AgentSessionBoardItem session -> session.projectId().value().toString();
                    default ->
                            item.id().contains(":")
                                    ? item.id().substring(0, item.id().indexOf(':'))
                                    : item.id();
                };
        return context.appState().projects().entrySet().stream()
                .filter(
                        entry ->
                                entry.getKey().value().toString().equals(id)
                                        || entry.getValue().name().equals(id))
                .map(java.util.Map.Entry::getValue)
                .findFirst();
    }
}
