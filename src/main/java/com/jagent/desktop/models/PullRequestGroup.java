package com.jagent.desktop.models;

import java.util.List;

/** Ordered presentation groups used by every pull-request board. */
public enum PullRequestGroup {
    NOT_READY("Not Ready"),
    WAITING_FOR_CHANGES("Waiting for Changes"),
    READY_FOR_REVIEW("Ready For Review"),
    APPROVED("Approved");

    private final String label;

    PullRequestGroup(final String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static List<PullRequestGroup> ordered() {
        return List.of(values());
    }
}
