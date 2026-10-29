package com.jagent.desktop.ui.views;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.jagent.desktop.api.RefreshableView;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.AsyncLoadingStateFactory;
import com.jagent.desktop.async.BatchListener;
import com.jagent.desktop.async.BoardInstanceState;
import com.jagent.desktop.models.board.AgentSessionBoardItem;
import com.jagent.desktop.models.board.BoardDefinition;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.models.board.BoardSource;
import com.jagent.desktop.models.board.sources.SourceConfigs;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.github.GitHubProvider;
import com.jagent.desktop.ui.board.KanbanBoard;
import com.jagent.desktop.ui.components.RefreshButton;
import com.jagent.desktop.ui.components.SearchInput;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.Nullable;

/** Global board combining pull requests, GitHub issues, Jira issues, and agent sessions. */
public final class BoardView extends JPanel implements RefreshableView {
    private static final String ALL = "All";
    private final transient ViewController viewController;
    private final transient AppState appState;
    private final transient BoardInstanceState boardInstanceState;
    private final KanbanBoard board;
    private final BoardDefinition definition;
    private final JLabel status = new JLabel("Loading board...");
    private final List<BoardItem> items = Collections.synchronizedList(new ArrayList<>());
    private final JComboBox<String> sourceFilter = new JComboBox<>();
    private final JComboBox<String> projectFilter = new JComboBox<>();
    private final JComboBox<String> groupFilter = new JComboBox<>();
    private final SearchInput searchInput =
            new SearchInput(new SearchInput.Text("board-search", "Search board", "Search board"));
    private final JComboBox<String> sortOrder =
            new JComboBox<>(
                    new String[] {
                        "Updated (newest first)",
                        "Updated (oldest first)",
                        "Created (newest first)",
                        "Created (oldest first)",
                        "Name (A-Z)",
                        "Name (Z-A)"
                    });
    private final RefreshButton refreshButton = new RefreshButton();
    private String message;
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger progress = new AtomicInteger(0);

    @AssistedInject
    public BoardView(
            final ViewController viewController,
            final AppState appState,
            final GitHubProvider githubProvider,
            final DialogService dialogService,
            final @Assisted BoardDefinition definition,
            final AsyncLoadingStateFactory asyncLoadingStateFactory) {
        super(new BorderLayout(0, 8));
        this.viewController = viewController;
        this.appState = appState;
        this.definition = definition;
        boardInstanceState = asyncLoadingStateFactory.getAsyncLoadingState(ViewId.BOARD);
        board =
                new KanbanBoard(
                        definition.config(),
                        viewController,
                        appState,
                        githubProvider,
                        dialogService,
                        ignored -> refresh(),
                        this::selectItem);
        configureFilters();
        final JPanel header = header();
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(board), BorderLayout.CENTER);
        boardInstanceState.setListener(
                definition.name(),
                new BatchListener<>() {

                    @Override
                    public void onComplete(final List<BoardItem> items) {
                        BoardView.this.items.clear();
                        BoardView.this.items.addAll(items);
                        BoardPresentation.updateFilterOptions(
                                appState, items, projectFilter, groupFilter);
                        applyPresentation();
                        refreshButton.setEnabled(true);
                        refreshButton.stop();
                        status.setText("");
                    }

                    @Override
                    public void onMessage(final String message) {
                        BoardView.this.message = message;
                        status.setText(message);
                        updateStatus();
                    }

                    @Override
                    public void onProgress(
                            final int p, @Nullable final List<BoardItem> progressData) {
                        progress.addAndGet(p);
                        if (progressData != null) {
                            items.addAll(progressData);
                            applyPresentation();
                        }
                        updateStatus();
                    }

                    @Override
                    public void onStart(final int t) {
                        items.clear();
                        applyPresentation();
                        refreshButton.setEnabled(false);
                        refreshButton.start();
                        total.set(t);
                        progress.set(0);
                    }

                    @Override
                    public void onFailure(final Throwable error) {
                        refreshButton.setEnabled(true);
                        refreshButton.stop();
                        status.setText("Failed to fetch board items: " + error.getMessage());
                    }
                });
        final Optional<List<BoardItem>> cachedItems =
                Objects.requireNonNullElse(
                        boardInstanceState.get(this.definition.name()), Optional.empty());
        cachedItems.ifPresentOrElse(
                cached -> {
                    items.addAll(cached);
                    BoardPresentation.updateFilterOptions(
                            appState, items, projectFilter, groupFilter);
                    applyPresentation();
                    refreshButton.setEnabled(true);
                    status.setText("");
                },
                () ->
                        boardInstanceState.fetch(
                                this.definition.name(), this.definition.config().sources()));
    }

    @FunctionalInterface
    public interface Factory {
        BoardView create(BoardDefinition definition);
    }

    private void updateStatus() {
        status.setText(
                String.format(
                        "%s Progress: %d/%d",
                        message != null ? message : "", progress.get(), total.get()));
    }

    private JPanel header() {
        final JPanel header = new JPanel(new BorderLayout());
        final JPanel titleAndStatus = new JPanel(new BorderLayout());
        titleAndStatus.add(new JLabel(definition.name()), BorderLayout.NORTH);
        titleAndStatus.add(status, BorderLayout.SOUTH);
        final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchInput.setVisible(true);
        controls.add(searchInput);
        controls.add(new JLabel("Source"));
        controls.add(sourceFilter);
        controls.add(new JLabel("Project"));
        controls.add(projectFilter);
        controls.add(new JLabel("Group"));
        controls.add(groupFilter);
        controls.add(new JLabel("Sort"));
        controls.add(sortOrder);
        refreshButton.addActionListener(event -> refresh());
        controls.add(refreshButton);
        final JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(titleAndStatus, BorderLayout.NORTH);
        left.add(controls, BorderLayout.SOUTH);
        header.add(left, BorderLayout.CENTER);
        return header;
    }

    private void configureFilters() {
        sourceFilter.addItem(ALL);
        for (final BoardSource source : enabledSources(definition.config().sources())) {
            sourceFilter.addItem(sourceLabel(source));
        }
        sourceFilter.addActionListener(event -> applyPresentation());
        projectFilter.addItem(ALL);
        groupFilter.addItem(ALL);
        projectFilter.addActionListener(event -> applyPresentation());
        groupFilter.addActionListener(event -> applyPresentation());
        sortOrder.addActionListener(event -> applyPresentation());
        searchInput.onChange(ignored -> applyPresentation());
    }

    private static List<BoardSource> enabledSources(final SourceConfigs sources) {
        final List<BoardSource> enabled = new ArrayList<>();
        if (sources.AGENT_SESSION().enabled()) {
            enabled.add(BoardSource.AGENT_SESSION);
        }
        if (sources.GITHUB_ISSUES().enabled()) {
            enabled.add(BoardSource.GITHUB_ISSUES);
        }
        if (sources.PULL_REQUEST().enabled()) {
            enabled.add(BoardSource.PULL_REQUEST);
        }
        if (sources.JIRA().enabled()) {
            enabled.add(BoardSource.JIRA);
        }
        return enabled;
    }

    public static String sourceLabel(final BoardSource source) {
        return switch (source) {
            case AGENT_SESSION -> "Agent Sessions";
            case GITHUB_ISSUES -> "GitHub Issues";
            case PULL_REQUEST -> "Pull Requests";
            case JIRA -> "Jira Issues";
        };
    }

    private void applyPresentation() {
        BoardPresentation.apply(
                appState,
                items,
                board,
                sourceFilter,
                projectFilter,
                groupFilter,
                sortOrder,
                searchInput.getText());
    }

    @Override
    public ViewId id() {
        return ViewId.BOARD;
    }

    @Override
    public String title() {
        return definition.name();
    }

    @Override
    public JPanel render() {
        return this;
    }

    @Override
    public CompletableFuture<?> refresh() {
        return boardInstanceState.fetch(this.definition.name(), this.definition.config().sources());
    }

    private void selectItem(final BoardItem item) {
        if (item instanceof AgentSessionBoardItem session) {
            openSession(session);
            return;
        }
        final var url = item.url();
        if (url != null) {
            PlatformCommands.openUrl(URI.create(url.toString()).toString());
        }
    }

    private void openSession(final AgentSessionBoardItem item) {
        if (appState.sessions().containsKey(item.sessionId())) {
            viewController.updateView(
                    ViewId.SESSION,
                    ViewController.ViewState.session(item.projectId(), item.sessionId()));
        }
    }
}
