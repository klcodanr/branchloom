package com.jagent.desktop.models.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import org.junit.jupiter.api.Test;

class JiraIssueBoardItemTest {
    @Test
    void keepsAuthorAndAssigneeAsSeparateFields() {
        final Date created = new Date(1_700_000_000_000L);
        final Date updated = new Date(1_700_100_000_000L);
        final JiraIssueBoardItem item =
                new JiraIssueBoardItem(
                        BoardSource.JIRA,
                        "PROJ-123",
                        "Add board filtering",
                        "Description",
                        created,
                        updated,
                        null,
                        "PROJ",
                        "In Progress",
                        "reporter@example.com",
                        "owner@example.com");

        assertEquals(created, item.createdAt(), "created date should be preserved");
        assertEquals(updated, item.updatedAt(), "updated date should be preserved");
        assertEquals("reporter@example.com", item.author(), "author email should be preserved");
        assertEquals("owner@example.com", item.assignee(), "assignee email should be preserved");
    }
}
