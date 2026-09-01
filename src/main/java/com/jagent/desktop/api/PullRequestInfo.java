package com.jagent.desktop.api;

/** Common pull-request fields used by UI presentation. */
public interface PullRequestInfo {
    int number();

    String title();

    String reviewDecision();

    String mergeState();

    boolean draft();

    int checksPassed();

    int checksTotal();

    String checksStatus();

    default String state() {
        return draft() ? "DRAFT" : "OPEN";
    }
}
