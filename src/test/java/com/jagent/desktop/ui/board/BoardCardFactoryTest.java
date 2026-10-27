package com.jagent.desktop.ui.board;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.jagent.desktop.models.board.BasicBoardItem;
import com.jagent.desktop.models.board.BoardSource;
import java.util.Date;
import org.junit.jupiter.api.Test;

class BoardCardFactoryTest {
    @Test
    void selectsGenericCardForBasicItems() {
        final BasicBoardItem item =
                new BasicBoardItem(
                        BoardSource.JIRA,
                        "1",
                        "Issue",
                        "",
                        new Date(),
                        new Date(),
                        null,
                        "project");

        assertInstanceOf(
                BoardCard.class,
                BoardCardFactory.create(
                        item,
                        null,
                        ignored -> java.util.concurrent.CompletableFuture.completedFuture(null)));
    }
}
