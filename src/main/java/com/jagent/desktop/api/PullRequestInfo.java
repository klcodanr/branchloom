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

    String REVIEW_QUEUE_REASON_READY =
            "Review requested, ready to inspect, and not blocked by checks or conflicts.";
    String REVIEW_QUEUE_REASON_WAITING =
            "Review requested, but the request is waiting on author, checks, or mergeability.";
    String REVIEW_QUEUE_FOCUS_DRAFT = "Confirm whether the draft is ready for review.";
    String REVIEW_QUEUE_FOCUS_FAILING =
            "Check failing CI before spending time on implementation details.";
    String REVIEW_QUEUE_FOCUS_CONFLICTING =
            "Confirm the conflict scope and whether a useful review is possible.";
    String REVIEW_QUEUE_FOCUS_GENERAL = "Review the change, checks, and recent comments.";

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

    default boolean readyForReviewQueue() {
        return !draft()
                && CHECKS_PASSING.equals(checksStatus())
                && switch (mergeState()) {
                    case MERGE_MERGEABLE,
                            MERGE_CLEAN,
                            MERGE_BEHIND,
                            MERGE_HAS_HOOKS,
                            MERGE_QUEUED ->
                            true;
                    default -> false;
                };
    }

    default boolean mergeActionAllowed() {
        return !draft() && !hasBlockingChecks() && !hasBlockingMergeability();
    }

    default String reviewQueueReason() {
        if (readyForReviewQueue()) {
            return REVIEW_QUEUE_REASON_READY;
        }
        return REVIEW_QUEUE_REASON_WAITING;
    }

    default String reviewQueueFocus() {
        if (draft()) {
            return REVIEW_QUEUE_FOCUS_DRAFT;
        }
        if (CHECKS_FAILING.equals(checksStatus())) {
            return REVIEW_QUEUE_FOCUS_FAILING;
        }
        if (MERGE_CONFLICTING.equals(mergeState())) {
            return REVIEW_QUEUE_FOCUS_CONFLICTING;
        }
        return REVIEW_QUEUE_FOCUS_GENERAL;
    }

    default String state() {
        return draft() ? "DRAFT" : "OPEN";
    }
}
