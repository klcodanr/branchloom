package com.jagent.desktop.models;

/** One status check entry reported for a pull request. */
public record PullRequestCheck(
        String name, String status, String conclusion, String detailsUrl, String details) {
    public PullRequestCheck {
        name = normalize(name);
        status = normalize(status);
        conclusion = normalize(conclusion);
        detailsUrl = normalize(detailsUrl);
        details = normalize(details);
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.trim();
    }
}
