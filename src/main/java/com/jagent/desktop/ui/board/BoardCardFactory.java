package com.jagent.desktop.ui.board;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.ui.components.CardPanel;
import com.jagent.desktop.ui.components.PullRequestCard;
import java.awt.Cursor;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import org.eclipse.jgit.annotations.NonNull;

public final class BoardCardFactory {
    private BoardCardFactory() {}

    public static CardPanel create(
            final BoardItem item,
            @NonNull final ActionContext actionContext,
            final Function<ProjectId, CompletableFuture<?>> refreshProject) {
        return create(item, actionContext, refreshProject, ignored -> {});
    }

    public static CardPanel create(
            final BoardItem item,
            @NonNull final ActionContext actionContext,
            final Function<ProjectId, CompletableFuture<?>> refreshProject,
            final Consumer<BoardItem> onSelect) {
        final CardPanel card;
        if (item instanceof PullRequestDetails request) {
            card = new PullRequestCard(actionContext, request, request, refreshProject);
        } else {
            card = new BoardCard(item);
        }
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registerSelectionClick(card, item, onSelect);
        return card;
    }

    private static void registerSelectionClick(
            final CardPanel card, final BoardItem item, final Consumer<BoardItem> onSelect) {
        final var listener =
                new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(final java.awt.event.MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 1) {
                            onSelect.accept(item);
                        }
                    }
                };
        addSelectionListener(card, listener);
    }

    private static void addSelectionListener(
            final java.awt.Container parent, final java.awt.event.MouseListener listener) {
        parent.addMouseListener(listener);
        for (final java.awt.Component child : parent.getComponents()) {
            if (child instanceof javax.swing.AbstractButton) {
                continue;
            }
            if (child instanceof java.awt.Container container) {
                addSelectionListener(container, listener);
            }
        }
    }
}
