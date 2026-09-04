package com.jagent.desktop.services;

import com.jagent.desktop.models.PullRequest;
import java.util.List;

/** Runs the optional app-level agent used to generate a review plan. */
public final class ReviewPlanAgent {
    private static final String SYSTEM_PROMPT =
            "You are generating a review plan for the top pull requests. Use only the supplied "
                    + "review requests and select the most important ones to review first. Do not "
                    + "invent pull requests or project facts. Return each selected pull request URL "
                    + "exactly once, in your recommended review order. You may append a short reason "
                    + "after each URL, but do not use a markdown code block.";

    private ReviewPlanAgent() {}

    public static String command(
            final String command, final String userPrompt, final List<PullRequest> requests) {
        final String prompt = composePrompt(userPrompt, requests);
        return command.contains("{prompt}")
                ? command.replace("{prompt}", PlatformCommands.shellQuote(prompt))
                : command + " " + PlatformCommands.shellQuote(prompt);
    }

    private static String composePrompt(final String userPrompt, final List<PullRequest> requests) {
        final StringBuilder prompt = new StringBuilder(512);
        prompt.append(SYSTEM_PROMPT)
                .append("\n\nUser instructions:\n")
                .append(userPrompt)
                .append("\n\nReview requests:\n");
        for (final PullRequest request : requests) {
            prompt.append("- Project: ")
                    .append(request.projectId().value().toString())
                    .append("; Number: #")
                    .append(request.number())
                    .append("; Title: ")
                    .append(request.title())
                    .append("; URL: ")
                    .append(request.url())
                    .append("; Draft: ")
                    .append(request.draft())
                    .append("; Checks: ")
                    .append(request.checksStatus())
                    .append("; Mergeability: ")
                    .append(request.mergeable())
                    .append('\n');
        }
        return prompt.toString();
    }
}
