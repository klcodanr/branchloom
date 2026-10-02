package com.jagent.desktop.models.git;

import java.util.List;

/**
 * A file or directory in a {@link ChangedFileTree}, addressed by its workspace-relative path.
 *
 * <p>Directory chains with a single child are folded into one node, so a node path may span several
 * path levels.
 */
public record ChangedFileNode(String path, boolean directory, List<ChangedFileNode> children) {

    public ChangedFileNode {
        children = List.copyOf(children);
    }
}
