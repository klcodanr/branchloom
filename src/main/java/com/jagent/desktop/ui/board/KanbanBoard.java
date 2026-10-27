package com.jagent.desktop.ui.board;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.board.BoardColumn;
import com.jagent.desktop.models.board.BoardConfig;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.ui.components.UiBorders;
import com.jagent.desktop.ui.components.UiConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public final class KanbanBoard extends JPanel {
    private transient BoardConfig config;
    private final transient ActionContext actionContext;
    private final transient Function<ProjectId, CompletableFuture<?>> refreshProject;
    private final transient Consumer<BoardItem> onSelect;
    private final transient BoardRuleEvaluator ruleEvaluator = new BoardRuleEvaluator();
    private List<BoardItem> items = List.of();

    public KanbanBoard(
            final BoardConfig config,
            final ActionContext actionContext,
            final Function<ProjectId, CompletableFuture<?>> refreshProject) {
        this(config, actionContext, refreshProject, ignored -> {});
    }

    public KanbanBoard(
            final BoardConfig config,
            final ActionContext actionContext,
            final Function<ProjectId, CompletableFuture<?>> refreshProject,
            final Consumer<BoardItem> onSelect) {
        super(new BorderLayout());
        this.config = config;
        this.actionContext = actionContext;
        this.refreshProject = refreshProject;
        this.onSelect = onSelect;
        render();
    }

    public void setConfig(final BoardConfig config) {
        this.config = config;
        render();
    }

    public void setItems(final List<BoardItem> items) {
        this.items = items == null ? List.of() : List.copyOf(items);
        render();
    }

    private void render() {
        final JPanel columns = new JPanel(new GridLayout(1, Math.max(1, config.columns().size())));
        final List<List<BoardItem>> assignedItems = assignItems();
        for (int columnIndex = 0; columnIndex < config.columns().size(); columnIndex++) {
            final var column = config.columns().get(columnIndex);
            final JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createTitledBorder(column.title()));
            final JPanel cards = new JPanel();
            cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
            cards.setAlignmentY(TOP_ALIGNMENT);
            for (final var item : assignedItems.get(columnIndex)) {
                final JPanel cardRow = new JPanel(new BorderLayout());
                cardRow.setOpaque(false);
                cardRow.setBorder(UiBorders.cardRow());
                cardRow.setMaximumSize(
                        new Dimension(Integer.MAX_VALUE, UiConstants.PR_CARD_HEIGHT + 4));
                cardRow.add(
                        BoardCardFactory.create(item, actionContext, refreshProject, onSelect),
                        BorderLayout.CENTER);
                cards.add(cardRow);
            }
            cards.add(Box.createVerticalGlue());
            final JScrollPane scroll =
                    new JScrollPane(
                            cards,
                            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.setPreferredSize(new Dimension(0, 0));
            panel.add(scroll, BorderLayout.CENTER);
            columns.add(panel);
        }
        removeAll();
        add(columns, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private List<List<BoardItem>> assignItems() {
        final List<List<BoardItem>> assigned = new java.util.ArrayList<>();
        config.columns().forEach(column -> assigned.add(new java.util.ArrayList<>()));
        for (final BoardItem item : items) {
            for (int columnIndex = 0; columnIndex < config.columns().size(); columnIndex++) {
                if (matches(config.columns().get(columnIndex), item)) {
                    assigned.get(columnIndex).add(item);
                    break;
                }
            }
        }
        return assigned;
    }

    private boolean matches(final BoardColumn column, final BoardItem item) {
        final String rule = column.rules().get(item.source());
        return rule != null && ruleEvaluator.matches(item, rule);
    }
}
