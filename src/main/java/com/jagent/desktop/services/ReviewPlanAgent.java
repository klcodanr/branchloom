package com.jagent.desktop.services;

import com.jagent.desktop.models.PullRequest;
import java.util.List;

/** Runs the optional app-level agent used to generate a review plan. */
public final class ReviewPlanAgent {
    private static final String PROMPT_PLACEHOLDER = "{prompt}";
    private static final String SYSTEM_PROMPT =
            "Task: Rank the supplied pull requests for review priority. Use only the supplied "
                    + "data. Do not invent pull requests, repositories, code facts, or timeline "
                    + "details.\n"
                    + "\n"
                    + "Output requirements (strict):\n"
                    + "1) Return results in descending review priority.\n"
                    + "2) Use exactly this 5-line block format per pull request:\n"
                    + "   <rank>. <url>\n"
                    + "   Change: <short change description>\n"
                    + "   Why now: <urgency rationale>\n"
                    + "   Focus: <review focus>\n"
                    + "   Blockers: <blockers or NONE>\n"
                    + "3) <rank> is an integer starting at 1 with no gaps.\n"
                    + "4) Keep Change, Why now, Focus, and Blockers concise (<= 20 words each).\n"
                    + "5) Use NONE when blockers are not present.\n"
                    + "6) Do not output markdown tables, headers, or code fences.";
    private static final int RECENT_COMMENT_LIMIT = 5;

    private ReviewPlanAgent() {}

    public static String command(
            final String command, final String userPrompt, final List<PullRequest> requests) {
        final String prompt = composePrompt(userPrompt, requests);
        return command.contains(PROMPT_PLACEHOLDER)
                ? command.replace(PROMPT_PLACEHOLDER, PlatformCommands.shellQuote(prompt))
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
                    .append("; Additions: +")
                    .append(request.additions())
                    .append("; Deletions: -")
                    .append(request.deletions())
                    .append("; Changed files: ")
                    .append(request.changedFiles())
                    .append("; Total changes: ")
                    .append(request.totalChanges())
                    .append("; Checks: ")
                    .append(request.checksStatus())
                    .append("; Mergeability: ")
                    .append(request.mergeable())
                    .append("; Recent comments (last ")
                    .append(RECENT_COMMENT_LIMIT)
                    .append("): ")
                    .append(request.commentSummary().isBlank() ? "None" : request.commentSummary())
                    .append('\n');
        }
        return prompt.toString();
    }
}
