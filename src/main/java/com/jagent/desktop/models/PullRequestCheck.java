package com.jagent.desktop.models;

import com.jagent.desktop.ui.components.Theme;
import java.awt.Color;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Set;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHCommitStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One status check entry reported for a pull request. */
public record PullRequestCheck(
        String name, Status status, Date completedAt, Conclusion conclusion, URL detailsUrl) {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestCheck.class);
    private static final Set<Conclusion> FAILING_CONCLUSIONS =
            Set.of(
                    Conclusion.ACTION_REQUIRED,
                    Conclusion.CANCELLED,
                    Conclusion.FAILURE,
                    Conclusion.TIMED_OUT);

    public enum Status {
        QUEUED,
        IN_PROGRESS,
        COMPLETED,
        UNKNOWN;

        public static Status from(final GHCheckRun.Status status) {
            return switch (status) {
                case QUEUED -> QUEUED;
                case IN_PROGRESS -> IN_PROGRESS;
                case COMPLETED -> COMPLETED;
                default -> UNKNOWN;
            };
        }
    }

    public enum Conclusion {
        ACTION_REQUIRED,
        CANCELLED,
        FAILURE,
        NEUTRAL,
        SUCCESS,
        SKIPPED,
        STALE,
        TIMED_OUT,
        UNKNOWN;

        public static Conclusion parse(final GHCheckRun.Conclusion conclusion) {
            if (conclusion == null) {
                return UNKNOWN;
            }
            try {
                return Conclusion.valueOf(conclusion.name());
            } catch (IllegalArgumentException exception) {
                return UNKNOWN;
            }
        }
    }

    public static PullRequestCheck from(final GHCheckRun check) {
        return new PullRequestCheck(
                check.getName() == null ? "" : check.getName().trim(),
                Status.from(check.getStatus()),
                check.getCompletedAt(),
                Conclusion.parse(check.getConclusion()),
                check.getDetailsUrl());
    }

    public static PullRequestCheck from(final GHCommitStatus status) {
        final String context = status.getContext() == null ? "" : status.getContext().trim();
        Date updatedAt = null;
        try {
            updatedAt = status.getUpdatedAt();
        } catch (IOException e) {
            LOG.warn("Failed to get updatedAt for commit status: context={}", context, e);
        }
        return fromCommitStatus(context, status.getState(), updatedAt, status.getTargetUrl());
    }

    /* package */ static PullRequestCheck fromCommitStatus(
            final String context,
            final GHCommitState state,
            final Date updatedAt,
            final String targetUrl) {
        final URL parsedTargetUrl = parseUrl(context, targetUrl);
        if (state == null) {
            return new PullRequestCheck(
                    context, Status.UNKNOWN, updatedAt, Conclusion.UNKNOWN, parsedTargetUrl);
        }
        return switch (state) {
            case SUCCESS ->
                    new PullRequestCheck(
                            context,
                            Status.COMPLETED,
                            updatedAt,
                            Conclusion.SUCCESS,
                            parsedTargetUrl);
            case PENDING ->
                    new PullRequestCheck(
                            context,
                            Status.IN_PROGRESS,
                            updatedAt,
                            Conclusion.UNKNOWN,
                            parsedTargetUrl);
            case ERROR, FAILURE ->
                    new PullRequestCheck(
                            context,
                            Status.COMPLETED,
                            updatedAt,
                            Conclusion.FAILURE,
                            parsedTargetUrl);
        };
    }

    private static URL parseUrl(final String context, final String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return null;
        }
        try {
            return new URL(targetUrl);
        } catch (MalformedURLException | IllegalArgumentException exception) {
            LOG.warn("Failed to parse targetUrl for commit status: context={}", context, exception);
            return null;
        }
    }

    public boolean passing() {
        return PullRequestCheck.Conclusion.SUCCESS.equals(conclusion())
                || PullRequestCheck.Conclusion.SKIPPED.equals(conclusion())
                || PullRequestCheck.Conclusion.NEUTRAL.equals(conclusion());
    }

    public boolean failing() {
        return FAILING_CONCLUSIONS.contains(conclusion());
    }

    public Color indicatorColor() {
        if (status() == Status.UNKNOWN) {
            return Theme.Colors.muted();
        } else if (status() == Status.IN_PROGRESS) {
            return Theme.Colors.warning();
        } else if (status() == Status.QUEUED) {
            return Theme.Colors.merge();
        }

        final var c = conclusion();
        if (c == PullRequestCheck.Conclusion.SUCCESS) {
            return Theme.Colors.success();
        } else if (failing()) {
            return Theme.Colors.danger();
        } else {
            return Theme.Colors.muted();
        }
    }
}
