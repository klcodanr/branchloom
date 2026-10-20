package com.jagent.desktop.models;

import java.time.Instant;

/** Latest pull-request observation for a session. */
public record SessionMonitoringStatus(
        PullRequest request, PullRequestDetails details, Instant checkedAt) {}
