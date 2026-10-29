package com.jagent.desktop.ui.board;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.jagent.desktop.models.board.BasicBoardItem;
import com.jagent.desktop.models.board.BoardSource;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.util.Date;
import java.util.Map;
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
                        TestAppState.controller(
                                new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of())),
                        new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of()),
                        null,
                        TestAppState.dialogService(),
                        ignored -> java.util.concurrent.CompletableFuture.completedFuture(null),
                        ignored -> {}));
    }
}
