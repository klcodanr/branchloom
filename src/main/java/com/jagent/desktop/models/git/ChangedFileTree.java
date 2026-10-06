package com.jagent.desktop.models.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A tree of workspace-relative changed file paths for a changed-files-only view.
 *
 * <p>Directory chains with a single child are folded into one node, so each node path extends to
 * the next branch point or file. Directories sort before files, and each level sorts by
 * case-insensitive name.
 */
public record ChangedFileTree(List<ChangedFileNode> children) {

    public ChangedFileTree {
        children = List.copyOf(children);
    }

    /**
     * Builds a tree from workspace-relative paths. Paths use '/' separators; a trailing slash marks
     * a directory entry. Blank, duplicate, and null entries are skipped.
     */
    public static ChangedFileTree of(final Collection<String> relativePaths) {
        final Set<String> directories = new HashSet<>();
        final Set<String> paths = new TreeSet<>();
        for (final String path : relativePaths) {
            final String normalized = normalize(path);
            if (normalized == null) {
                continue;
            }
            paths.add(normalized);
            if (path.trim().replace('\\', '/').endsWith("/")) {
                directories.add(normalized);
            }
        }
        final Map<String, Set<String>> childrenByPath = index(paths);
        final List<ChangedFileNode> root = buildNodes("", childrenByPath, directories);
        root.replaceAll(ChangedFileTree::fold);
        sortChildren(root);
        return new ChangedFileTree(root);
    }

    private static String normalize(final String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static Map<String, Set<String>> index(final Set<String> paths) {
        final Map<String, Set<String>> childrenByPath = new HashMap<>();
        for (final String path : paths) {
            String parent = "";
            for (final String segment : path.split("/")) {
                final String child = parent.isEmpty() ? segment : parent + '/' + segment;
                childrenByPath.computeIfAbsent(parent, ignored -> new TreeSet<>()).add(child);
                parent = child;
            }
        }
        return childrenByPath;
    }

    private static List<ChangedFileNode> buildNodes(
            final String parent,
            final Map<String, Set<String>> childrenByPath,
            final Set<String> directories) {
        final List<ChangedFileNode> nodes = new ArrayList<>();
        for (final String path : childrenByPath.getOrDefault(parent, Set.of())) {
            final Set<String> children = childrenByPath.get(path);
            final boolean directory = directories.contains(path) || children != null;
            final List<ChangedFileNode> childNodes =
                    directory ? buildNodes(path, childrenByPath, directories) : List.of();
            nodes.add(new ChangedFileNode(path, directory, childNodes));
        }
        sortChildren(nodes);
        return nodes;
    }

    private static ChangedFileNode fold(final ChangedFileNode node) {
        if (!node.directory()) {
            return node;
        }
        final List<ChangedFileNode> folded = new ArrayList<>(node.children().size());
        for (final ChangedFileNode child : node.children()) {
            folded.add(fold(child));
        }
        sortChildren(folded);
        if (folded.size() > 1) {
            return new ChangedFileNode(node.path(), true, folded);
        }
        return folded.isEmpty() ? node : folded.get(0);
    }

    private static void sortChildren(final List<ChangedFileNode> children) {
        children.sort(
                Comparator.comparing((ChangedFileNode node) -> !node.directory())
                        .thenComparing(
                                ChangedFileTree::lastSegment, String.CASE_INSENSITIVE_ORDER));
    }

    private static String lastSegment(final ChangedFileNode node) {
        final String path = node.path();
        final int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }
}
