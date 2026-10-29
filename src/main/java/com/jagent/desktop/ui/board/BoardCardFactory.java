package com.jagent.desktop.ui.board;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.github.GitHubProvider;
import com.jagent.desktop.ui.components.CardPanel;
import com.jagent.desktop.ui.components.PullRequestCard;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.eclipse.jgit.annotations.NonNull;

public final class BoardCardFactory {
    private BoardCardFactory() {}

    public static CardPanel create(
            final BoardItem item,
            @NonNull final ViewController viewController,
            final AppState appState,
            final GitHubProvider githubProvider,
            final DialogService dialogService,
            final Function<ProjectId, CompletableFuture<?>> refreshProject,
            final Consumer<BoardItem> onSelect) {
        final CardPanel card;
        if (item instanceof PullRequestDetails request) {
            card =
                    new PullRequestCard(
                            viewController,
                            appState,
                            githubProvider,
                            dialogService,
                            request,
                            request,
                            refreshProject);
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
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 1) {
                            onSelect.accept(item);
                        }
                    }
                };
        addSelectionListener(card, listener);
    }

    private static void addSelectionListener(final Container parent, final MouseListener listener) {
        parent.addMouseListener(listener);
        for (final Component child : parent.getComponents()) {
            if (child instanceof AbstractButton) {
                continue;
            }
            if (child instanceof Container container) {
                addSelectionListener(container, listener);
            }
        }
    }
}
