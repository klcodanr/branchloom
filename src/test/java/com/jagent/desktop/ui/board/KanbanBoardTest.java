package com.jagent.desktop.ui.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.board.BasicBoardItem;
import com.jagent.desktop.models.board.BoardColumn;
import com.jagent.desktop.models.board.BoardConfig;
import com.jagent.desktop.models.board.BoardSource;
import java.awt.Component;
import java.awt.Container;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class KanbanBoardTest {
    @Test
    void rendersItemsUsingColumnRulesAndSupportsUpdates() {
        final BoardConfig config =
                new BoardConfig(
                        List.of(
                                new BoardColumn("Todo", Map.of(BoardSource.JIRA, "true")),
                                new BoardColumn("Done", Map.of(BoardSource.JIRA, "true"))),
                        null);
        final KanbanBoard board =
                GuiActionRunner.execute(
                        () ->
                                new KanbanBoard(
                                        config,
                                        null,
                                        ignored ->
                                                java.util.concurrent.CompletableFuture
                                                        .completedFuture(null)));

        GuiActionRunner.execute(
                () -> board.setItems(List.of(item("Open issue"), item("Closed issue"))));

        final String text = componentText(board);
        assertTrue(text.contains("Open issue"), "open issue should render");
        assertTrue(text.contains("Closed issue"), "closed issue should render");
        assertTrue(
                count(text, "Open issue") == 1 && count(text, "Closed issue") == 1,
                "each item should be assigned to only the first matching column");

        GuiActionRunner.execute(
                () ->
                        board.setConfig(
                                new BoardConfig(
                                        List.of(
                                                new BoardColumn(
                                                        "Work", Map.of(BoardSource.JIRA, "true"))),
                                        null)));
        assertTrue(board.getComponentCount() > 0, "updated board should render components");
    }

    private static BasicBoardItem item(final String title) {
        final Date now = new Date();
        return new BasicBoardItem(BoardSource.JIRA, title, title, "", now, now, null, "project");
    }

    private static String componentText(final Component component) {
        if (component instanceof JLabel label) {
            return label.getText() == null ? "" : label.getText();
        }
        if (component instanceof Container container) {
            final StringBuilder text = new StringBuilder();
            for (final Component child : container.getComponents()) {
                text.append(componentText(child)).append(' ');
            }
            return text.toString();
        }
        return "";
    }

    private static int count(final String text, final String value) {
        return text.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }
}
