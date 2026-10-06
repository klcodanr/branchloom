package com.jagent.desktop.models.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangedFileTreeTest {

    @Test
    void emptyInputProducesNoChildren() {
        assertTrue(
                ChangedFileTree.of(List.of()).children().isEmpty(),
                "empty input should have no children");
    }

    @Test
    void singleFileIsNotFolded() {
        final ChangedFileTree tree = ChangedFileTree.of(List.of("README.md"));

        assertEquals(1, tree.children().size(), "single file should produce one child");
        final ChangedFileNode node = tree.children().get(0);
        assertEquals("README.md", node.path(), "file path should be preserved");
        assertFalse(node.directory(), "file should not be a directory");
        assertTrue(node.children().isEmpty(), "file should have no children");
    }

    @Test
    void singleChildDirectoryChainsFoldIntoOneLeaf() {
        final ChangedFileTree tree =
                ChangedFileTree.of(List.of("src/main/java/com/jagent/App.java"));

        assertEquals(1, tree.children().size(), "folded chain should produce one child");
        final ChangedFileNode node = tree.children().get(0);
        assertEquals(
                "src/main/java/com/jagent/App.java", node.path(), "folded path should be complete");
        assertFalse(node.directory(), "folded chain should become a file leaf");
        assertTrue(node.children().isEmpty(), "folded file should have no children");
    }

    @Test
    void branchPointStopsFolding() {
        final ChangedFileTree tree = ChangedFileTree.of(List.of("a/b/c/x.txt", "a/b/c/y.txt"));

        assertEquals(1, tree.children().size(), "shared branch should produce one root child");
        final ChangedFileNode node = tree.children().get(0);
        assertEquals("a/b/c", node.path(), "branch point should keep its directory path");
        assertTrue(node.directory(), "branch point should remain a directory");
        assertEquals(
                List.of("a/b/c/x.txt", "a/b/c/y.txt"),
                node.children().stream().map(ChangedFileNode::path).toList(),
                "branch children should be preserved");
    }

    @Test
    void directoryWithSingleFileChildFoldsIntoFile() {
        final ChangedFileTree tree = ChangedFileTree.of(List.of("a/b.txt", "a/c/d.txt"));

        assertEquals(1, tree.children().size(), "root should contain the branch directory");
        final ChangedFileNode a = tree.children().get(0);
        assertEquals("a", a.path(), "directory with two children should stay a directory");
        assertTrue(a.directory(), "directory with two children should remain a directory");
        assertEquals(
                List.of("a/b.txt", "a/c/d.txt"),
                a.children().stream().map(ChangedFileNode::path).toList(),
                "directory children should be preserved");
        a.children()
                .forEach(
                        child ->
                                assertFalse(
                                        child.directory(),
                                        "single file child should fold into the parent"));
    }

    @Test
    void childrenSortDirectoriesFirstCaseInsensitively() {
        final ChangedFileTree tree =
                ChangedFileTree.of(
                        List.of("z.txt", "Beta/b.txt", "beta/c.txt", "a/x.txt", "a/y.txt"));

        assertEquals(
                List.of("a", "Beta/b.txt", "beta/c.txt", "z.txt"),
                tree.children().stream().map(ChangedFileNode::path).toList(),
                "children should be sorted with directories first");
        assertTrue(tree.children().get(0).directory(), "branch directory should stay a directory");
        assertFalse(
                tree.children().get(1).directory(),
                "single-child directory should fold into a file");
    }

    @Test
    void markedDirectoryWithoutDeeperPathsIsDirectoryLeaf() {
        final ChangedFileTree tree = ChangedFileTree.of(List.of("build/"));

        assertEquals(1, tree.children().size(), "marked directory should produce one child");
        final ChangedFileNode node = tree.children().get(0);
        assertEquals("build", node.path(), "trailing slash should be stripped");
        assertTrue(node.directory(), "trailing slash should mark a directory");
        assertTrue(node.children().isEmpty(), "directory leaf should have no children");
    }

    @Test
    void backslashSeparatorsAreNormalized() {
        final ChangedFileTree tree = ChangedFileTree.of(List.of("src\\App.java"));

        assertEquals("src/App.java", tree.children().get(0).path(), "backslashes should normalize");
    }

    @Test
    void duplicatesAndBlankEntriesAreSkipped() {
        final ChangedFileTree tree =
                ChangedFileTree.of(Arrays.asList("a.txt", "a.txt", "  ", "/", null));

        assertEquals(
                List.of("a.txt"),
                tree.children().stream().map(ChangedFileNode::path).toList(),
                "duplicates and blank entries should be skipped");
    }

    @Test
    void deepMixKeepsBranchPointsAndFoldsChains() {
        final ChangedFileTree tree =
                ChangedFileTree.of(
                        List.of(
                                "src/main/java/App.java",
                                "src/main/resources/i18n/en.properties",
                                "docs/guide.md"));

        assertEquals(2, tree.children().size(), "mixed input should produce two root children");
        final ChangedFileNode branch = tree.children().get(0);
        assertEquals("src/main", branch.path(), "branch path should stop at the branch point");
        assertTrue(branch.directory(), "branch point should remain a directory");
        assertEquals(
                List.of("src/main/java/App.java", "src/main/resources/i18n/en.properties"),
                branch.children().stream().map(ChangedFileNode::path).toList(),
                "branch files should be preserved");
        final ChangedFileNode guide = tree.children().get(1);
        assertEquals("docs/guide.md", guide.path(), "single-child documentation path should fold");
        assertFalse(guide.directory(), "folded documentation path should be a file");
    }
}
