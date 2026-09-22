package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestFilter;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Pull requests authored by the current user across all projects. */
public final class MyPullRequestsView extends JPanel implements View {
    private static final String DEFAULT_FILTER = "My PRs";
    private final transient PullRequestCache pullRequestCache;
    private final JComboBox<PullRequestFilter> filters;
    private final PullRequestsBoard board;

    public MyPullRequestsView(final ActionContext actionContext) {
        super(new BorderLayout());
        this.pullRequestCache = PullRequestCache.get(actionContext.appState());
        filters =
                new JComboBox<>(
                        actionContext
                                .appState()
                                .appSettings()
                                .pullRequestFilters()
                                .toArray(PullRequestFilter[]::new));
        final PullRequestFilter defaultFilter =
                actionContext.appState().appSettings().filterNamed(DEFAULT_FILTER);
        filters.setSelectedItem(defaultFilter);
        board = new PullRequestsBoard(actionContext, defaultFilter.query(), this::pullRequests);
        filters.addActionListener(
                event -> {
                    board.setQuery(selectedFilter().query());
                    board.refresh();
                });
        final JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.add(new JLabel("Filter"));
        filterRow.add(filters);
        add(filterRow, BorderLayout.NORTH);
        add(TabBody.wrap(board), BorderLayout.CENTER);
    }

    @Override
    public ViewId id() {
        return ViewId.MY_PULL_REQUESTS;
    }

    @Override
    public String title() {
        return "Pull Requests";
    }

    @Override
    public JPanel render() {
        return this;
    }

    @Override
    public void refresh() {
        board.refresh();
    }

    @Override
    public void detach() {}

    private List<PullRequest> pullRequests(final String query) {
        return pullRequestCache.loadForFilter(
                new PullRequestFilter(selectedFilter().name(), query));
    }

    private PullRequestFilter selectedFilter() {
        final PullRequestFilter selected = (PullRequestFilter) filters.getSelectedItem();
        if (selected != null) {
            return selected;
        }
        return new PullRequestFilter(DEFAULT_FILTER, "");
    }
}
