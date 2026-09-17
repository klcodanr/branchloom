package com.jagent.desktop.api;

/** Common pull-request fields used by UI presentation. */
public interface PullRequestInfo {
    String CHECKS_FAILING = "FAILING";
    String CHECKS_PENDING = "PENDING";
    String CHECKS_PASSING = "PASSING";

    String MERGE_CONFLICTING = "CONFLICTING";
    String MERGE_DIRTY = "DIRTY";
    String MERGE_BLOCKED = "BLOCKED";
    String MERGE_DRAFT = "DRAFT";
    String MERGE_UNKNOWN = "UNKNOWN";
    String MERGE_UNSTABLE = "UNSTABLE";
    String MERGE_MERGEABLE = "MERGEABLE";
    String MERGE_CLEAN = "CLEAN";
    String MERGE_BEHIND = "BEHIND";
    String MERGE_HAS_HOOKS = "HAS_HOOKS";
    String MERGE_QUEUED = "QUEUED";

    int number();

    String title();

    String reviewDecision();

    String mergeState();

    boolean draft();

    int checksPassed();

    int checksTotal();

    String checksStatus();

    default boolean hasBlockingChecks() {
        return CHECKS_FAILING.equals(checksStatus()) || CHECKS_PENDING.equals(checksStatus());
    }

    default boolean hasBlockingMergeability() {
        return switch (mergeState()) {
            case MERGE_CONFLICTING,
                    MERGE_DIRTY,
                    MERGE_BLOCKED,
                    MERGE_DRAFT,
                    MERGE_UNKNOWN,
                    MERGE_UNSTABLE ->
                    true;
            default -> false;
        };
    }

    default boolean hasBlockingMergeabilityForBoardGrouping() {
        return switch (mergeState()) {
            case MERGE_CONFLICTING, MERGE_DIRTY, MERGE_UNKNOWN, MERGE_UNSTABLE -> true;
            default -> false;
        };
    }

    default boolean mergeActionAllowed() {
        return !draft() && !hasBlockingChecks() && !hasBlockingMergeability();
    }

    default String state() {
        return draft() ? "DRAFT" : "OPEN";
    }
}
