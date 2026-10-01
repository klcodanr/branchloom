package com.jagent.desktop.models;

import com.jagent.desktop.ui.components.Theme;
import java.awt.Color;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record PullRequestDetails(
        @NotNull ProjectId projectId,
        @NotNull Project project,
        @NotNull int number,
        @NotNull boolean draft,
        @NotNull boolean mergeable,
        @NotNull String mergableState,
        @NotNull int additions,
        @NotNull int deletions,
        @NotNull int changedFiles) {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestDetails.class);

    public enum Status {
        READY,
        CONFLICTED,
        DRAFT,
        PENDING,
        FAILED,
        OTHER
    }

    public Status status() {
        LOG.debug(
                "Getting PR status: number={}, draft={}, mergeable={}, mergableState={}",
                number(),
                draft(),
                mergeable(),
                mergableState());
        if (draft()) {
            return Status.DRAFT;
        } else if (mergeable && List.of("clean", "behind").contains(mergableState())) {
            return Status.READY;
        } else if ("dirty".equals(mergableState())) {
            return Status.CONFLICTED;
        } else if (List.of("blocked", "draft", "has_hooks").contains(mergableState())) {
            return Status.PENDING;
        } else if ("unstable".equals(mergableState())) {
            return Status.FAILED;
        }
        return Status.OTHER;
    }

    public Color indicatorColor() {
        final Status stat = status();
        if (draft()) {
            return Theme.mutedColor();
        } else if (stat == Status.READY) {
            return Theme.successColor();
        } else if (stat == Status.PENDING) {
            return Theme.warningColor();
        } else if (stat == Status.FAILED || stat == Status.CONFLICTED) {
            return Theme.dangerColor();
        }
        return Theme.mutedColor();
    }
}
