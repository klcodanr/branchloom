package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestGroupModelTest {
    @Test
    void orderedGroupsRemainStable() {
        assertEquals(
                List.of(
                        PullRequestGroup.NOT_READY,
                        PullRequestGroup.WAITING_FOR_CHANGES,
                        PullRequestGroup.READY_FOR_REVIEW,
                        PullRequestGroup.APPROVED),
                PullRequestGroup.ordered(),
                "ordered pull request groups should remain stable");
    }
}
