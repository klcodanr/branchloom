package com.jagent.desktop.services;

import com.jagent.desktop.api.PullRequestInfo;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GitHub {
    private static final int PR_PAGE_SIZE = 25;
    private static final String PR_QUERY =
            "query($search:String!, $endCursor:String, $pageSize:Int!) {"
                    + " search(query:$search, type:ISSUE, first:$pageSize, after:$endCursor) {"
                    + " nodes { ... on PullRequest { number title bodyText url createdAt updatedAt isDraft"
                    + " reviewDecision mergeable mergeStateStatus author { login } headRefName"
                    + " comments(last:3) { nodes { bodyText author { login } } }"
                    + " statusCheckRollup { contexts(first:100) { nodes {"
                    + " ... on CheckRun { conclusion status }"
                    + " ... on StatusContext { state }"
                    + " } } } } }"
                    + " pageInfo { hasNextPage endCursor } } }";
    private static final String PR_JQ =
            ".data.search.nodes[] | [.number, .title, (.bodyText // \"\"), ([.comments.nodes[]? | ((.author.login // \"unknown\") + \": \" + (.bodyText // \"\") | gsub(\"[\\\\t\\\\r\\\\n]+\"; \" \") )] | join(\" | \") ), .url, .createdAt, .updatedAt, .reviewDecision, .mergeable, .isDraft, .author.login, .headRefName, ([.statusCheckRollup.contexts.nodes[]? | select((.conclusion // .state) == \"SUCCESS\" or (.conclusion // .state) == \"SKIPPED\" or (.conclusion // .state) == \"NEUTRAL\")] | length), (.statusCheckRollup.contexts.nodes | length), (if any(.statusCheckRollup.contexts.nodes[]?; (.conclusion // .state) == \"FAILURE\" or (.conclusion // .state) == \"ERROR\") then \"FAILING\" elif any(.statusCheckRollup.contexts.nodes[]?; (.status // \"\") != \"COMPLETED\" and (.state // \"\") != \"SUCCESS\" and (.state // \"\") != \"FAILURE\") then \"PENDING\" elif (.statusCheckRollup.contexts.nodes | length) == 0 then \"UNKNOWN\" else \"PASSING\" end)] | @tsv";

    public record Auth(String host, String user) {
        @Override
        public String toString() {
            return user + " (" + host + ")";
        }
    }

    private GitHub() {}

    public record PullRequestDetails(
            int number,
            String title,
            String state,
            String reviewDecision,
            String mergeState,
            String url,
            boolean draft,
            int checksPassed,
            int checksTotal,
            String checksStatus)
            implements PullRequestInfo {}

    public static List<Auth> configuredAuths() {
        try {
            final String expression =
                    ".hosts | to_entries[] | .key as $host | .value[] | [$host, .login] | @tsv";
            final ProcessBuilder builder =
                    PlatformCommands.prepare(
                                    new ProcessBuilder(
                                            PlatformCommands.executable("gh"),
                                            "auth",
                                            "status",
                                            "--json",
                                            "hosts",
                                            "--jq",
                                            expression))
                            .redirectErrorStream(true);
            final Process process = builder.start();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                PlatformCommands.logFailure(builder, process.exitValue(), output);
                return List.of();
            }
            return output.lines()
                    .map(line -> line.split("\\t", 2))
                    .filter(
                            values ->
                                    values.length == 2
                                            && !values[0].isBlank()
                                            && !values[1].isBlank())
                    .map(values -> new Auth(values[0], values[1]))
                    .distinct()
                    .toList();
        } catch (IOException exception) {
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    public static List<PullRequest> loadForProject(final ProjectId projectId, final Project project)
            throws IOException, InterruptedException {
        return load(projectId, project, "author:@me");
    }

    public static List<PullRequest> loadReviewRequestedForProject(
            final ProjectId projectId, final Project project)
            throws IOException, InterruptedException {
        return load(projectId, project, "review-requested:@me -author:@me");
    }

    public static PullRequestDetails loadCurrent(final Project project, final Path worktree)
            throws IOException, InterruptedException {
        final String command =
                Git.githubCommand(
                        project,
                        "gh pr view --json number,title,state,url,reviewDecision,mergeStateStatus,isDraft,statusCheckRollup --jq '[.number, .title, .state, .reviewDecision, .mergeStateStatus, .url, .isDraft, ([.statusCheckRollup[]? | select((.conclusion // .state) == \"SUCCESS\" or (.conclusion // .state) == \"SKIPPED\" or (.conclusion // .state) == \"NEUTRAL\")] | length), (.statusCheckRollup | length), (if any(.statusCheckRollup[]?; (.conclusion // .state) == \"FAILURE\" or (.conclusion // .state) == \"ERROR\") then \"FAILING\" elif any(.statusCheckRollup[]?; (.status // \"\") != \"COMPLETED\" and (.state // \"\") != \"SUCCESS\" and (.state // \"\") != \"FAILURE\") then \"PENDING\" elif (.statusCheckRollup | length) == 0 then \"UNKNOWN\" else \"PASSING\" end)] | @tsv'");
        final ProcessBuilder builder =
                PlatformCommands.prepare(new ProcessBuilder(PlatformCommands.shell(command)))
                        .directory(worktree.toFile())
                        .redirectErrorStream(true);
        final Process process = builder.start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            PlatformCommands.logFailure(builder, process.exitValue(), output);
            throw new IOException(output.trim());
        }
        final String[] values = output.trim().split("\\t", -1);
        if (values.length < 10) {
            throw new IOException("No pull request found");
        }
        return new PullRequestDetails(
                Integer.parseInt(values[0]),
                values[1],
                values[2],
                values[3],
                values[4],
                values[5],
                Boolean.parseBoolean(values[6]),
                Integer.parseInt(values[7]),
                Integer.parseInt(values[8]),
                values[9]);
    }

    private static List<PullRequest> load(
            final ProjectId projectId, final Project project, final String search)
            throws IOException, InterruptedException {
        final Path projectPath = Path.of(project.path());
        final String repository = repositoryName(projectPath);
        if (repository == null) {
            throw new IOException("No GitHub remote found for this project");
        }
        final String query =
                "gh api graphql --paginate -F pageSize="
                        + PR_PAGE_SIZE
                        + " -f search="
                        + Git.shellQuote("repo:" + repository + " is:pr is:open " + search)
                        + " -f query="
                        + Git.shellQuote(PR_QUERY)
                        + " --jq "
                        + Git.shellQuote(PR_JQ);
        final ProcessBuilder builder =
                PlatformCommands.prepare(new ProcessBuilder(PlatformCommands.shell(query)))
                        .directory(projectPath.toFile())
                        .redirectErrorStream(true);
        final Process process = builder.start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            PlatformCommands.logFailure(builder, process.exitValue(), output);
            throw new IOException(output.trim());
        }
        final List<PullRequest> requests = new ArrayList<>();
        for (final String line : output.split("\\R")) {
            final PullRequestRow row = parse(line);
            if (row != null) {
                requests.add(
                        new PullRequest(
                                projectId,
                                row.number(),
                                row.title(),
                                row.description(),
                                row.commentSummary(),
                                row.url(),
                                row.createdAt(),
                                row.updatedAt(),
                                row.reviewDecision(),
                                row.mergeable(),
                                row.draft(),
                                row.author(),
                                row.headBranch(),
                                row.checksPassed(),
                                row.checksTotal(),
                                row.checksStatus()));
            }
        }
        return requests;
    }

    private static PullRequestRow parse(final String line) {
        final String[] values = line.split("\\t", -1);
        if (values.length < 15) {
            return null;
        }
        return new PullRequestRow(
                Integer.parseInt(values[0]),
                values[1],
                values[2],
                values[3],
                values[4],
                values[5],
                values[6],
                values[7],
                values[8],
                Boolean.parseBoolean(values[9]),
                values[10],
                values[11],
                Integer.parseInt(values[12]),
                Integer.parseInt(values[13]),
                values[14]);
    }

    private record PullRequestRow(
            int number,
            String title,
            String description,
            String commentSummary,
            String url,
            String createdAt,
            String updatedAt,
            String reviewDecision,
            String mergeable,
            boolean draft,
            String author,
            String headBranch,
            int checksPassed,
            int checksTotal,
            String checksStatus) {}

    private static String repositoryName(final Path path) throws IOException, InterruptedException {
        final ProcessBuilder builder =
                PlatformCommands.prepare(
                                new ProcessBuilder(
                                        PlatformCommands.executable("git"),
                                        "config",
                                        "--get",
                                        "remote.origin.url"))
                        .directory(path.toFile())
                        .redirectErrorStream(true);
        final Process process = builder.start();
        String remote =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0 || remote.isBlank()) {
            if (process.exitValue() != 0) {
                PlatformCommands.logFailure(builder, process.exitValue(), remote);
            }
            return null;
        }
        if (remote.endsWith(".git")) {
            remote = remote.substring(0, remote.length() - 4);
        }
        if (remote.startsWith("git@")) {
            final int colon = remote.indexOf(':');
            return colon < 0 ? null : remote.substring(colon + 1);
        }
        final URI uri = URI.create(remote);
        final String remotePath = uri.getPath();
        return remotePath == null ? null : remotePath.replaceFirst("^/", "");
    }
}
