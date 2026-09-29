package com.jagent.desktop.models;

import java.util.List;

/** A collection of status check entries reported for a pull request. */
public record PullRequestChecks(List<PullRequestCheck> checks) {

    public enum Status {
        UNKNOWN,
        FAILING,
        PENDING,
        PASSING;
    }

    public Status checksStatus() {
        if (checks.isEmpty()) {
            return Status.UNKNOWN;
        }
        if (checks.stream().anyMatch(PullRequestCheck::failing)) {
            return Status.FAILING;
        }
        if (checks.stream().anyMatch(check -> !check.passing())) {
            return Status.PENDING;
        }
        return Status.PASSING;
    }

    public int passed() {
        return (int) checks.stream().filter(PullRequestCheck::passing).count();
    }

    public int total() {
        return checks.size();
    }
}
