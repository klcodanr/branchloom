package com.jagent.desktop.ui.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.board.BasicBoardItem;
import com.jagent.desktop.models.board.BoardItem;
import com.jagent.desktop.models.board.BoardSource;
import java.util.Date;
import org.junit.jupiter.api.Test;

class BoardRuleEvaluatorTest {
    private final BoardRuleEvaluator evaluator = new BoardRuleEvaluator();
    private final BoardItem item =
            new BasicBoardItem(
                    BoardSource.PULL_REQUEST,
                    "1",
                    "Fix login",
                    "Authentication fix",
                    new Date(),
                    new Date(),
                    null,
                    "project");

    @Test
    void evaluatesFieldsAndCommonProperties() {
        assertTrue(
                evaluator.matches(item, "source == 'PULL_REQUEST' && title == 'Fix login'"),
                "matching source and title should pass");
    }

    @Test
    void blankRuleMatches() {
        assertTrue(evaluator.matches(item, " "), "blank rules should match");
        assertTrue(evaluator.matches(item, null), "null rules should match");
    }

    @Test
    void falseOrInvalidRuleDoesNotMatch() {
        assertFalse(
                evaluator.matches(item, "state == 'CLOSED'"), "a non-matching state should fail");
        assertFalse(evaluator.matches(item, "state =="), "an invalid rule should fail");
        assertFalse(evaluator.matches(item, "title"), "a non-boolean rule result should not match");
    }

    @Test
    void evaluatesCommonPropertiesAndNullValues() {
        assertTrue(
                evaluator.matches(item, "id == '1' && description == 'Authentication fix'"),
                "id and description should be available to rules");
        assertTrue(
                evaluator.matches(item, "url == null && boardProjectId == 'project'"),
                "null URL and board project should be available to rules");
    }
}
