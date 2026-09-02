package com.jagent.desktop.services.git;

import com.jagent.desktop.services.Git;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GitParser {
    private GitParser() {}

    public static List<Git.Branch> parseBranches(final List<String> lines) {
        return lines.stream()
                .map(line -> line.split("\\|", 2))
                .filter(parts -> parts.length > 0 && !parts[0].isBlank())
                .filter(parts -> parts.length < 2 || parts[1].isBlank())
                .map(
                        parts -> {
                            final boolean remote = parts[0].startsWith("refs/remotes/");
                            final String name = parts[0].substring(remote ? 13 : 11);
                            return new Git.Branch(name, remote);
                        })
                .filter(branch -> !branch.name().endsWith("/HEAD"))
                .distinct()
                .toList();
    }

    public static Map<String, String> parseStatus(final String output) {
        final Map<String, String> statuses = new LinkedHashMap<>();
        final String[] entries = output.split("\u0000", -1);
        int index = 0;
        while (index < entries.length) {
            final String entry = entries[index];
            if (entry.length() < 4) {
                index++;
                continue;
            }
            final String code = entry.substring(0, 2);
            final String path = entry.substring(3);
            statuses.put(path, code);
            index += code.contains("R") || code.contains("C") ? 2 : 1;
        }
        return Map.copyOf(statuses);
    }

    public static Git.WorktreeStatus parseWorktreeStatus(final String output) {
        final Map<String, String> files = parseStatus(output);
        final int additions =
                (int)
                        files.values().stream()
                                .filter(status -> "??".equals(status) || status.contains("A"))
                                .count();
        final int deletions =
                (int)
                        files.values().stream()
                                .filter(status -> !status.contains("A") && status.contains("D"))
                                .count();
        return new Git.WorktreeStatus(
                files, additions, files.size() - additions - deletions, deletions);
    }

    public static List<Git.Worktree> parseWorktrees(final List<String> lines) {
        final List<Git.Worktree> worktrees = new ArrayList<>();
        Path path = null;
        String branch = null;
        boolean prunable = false;
        for (final String line : lines) {
            if (line.startsWith("worktree ")) {
                if (path != null) {
                    worktrees.add(new Git.Worktree(path, branch, prunable));
                }
                path = Path.of(line.substring("worktree ".length()));
                branch = null;
                prunable = false;
            } else if (line.startsWith("branch ")) {
                branch = line.substring("branch ".length());
            } else if (line.startsWith("prunable ")) {
                prunable = true;
            }
        }
        if (path != null) {
            worktrees.add(new Git.Worktree(path, branch, prunable));
        }
        return worktrees;
    }
}
