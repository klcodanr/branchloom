package com.jagent.desktop.models.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BranchTest {
    private static final String ORIGIN_MAIN = "origin/main";

    @Test
    void displayNameStripsHeadsPrefix() {
        final Branch branch = new Branch("refs/heads/main");

        assertEquals("main", branch.displayName(), "heads ref prefix should be stripped");
    }

    @Test
    void displayNameStripsRemotesPrefix() {
        final Branch branch = new Branch("refs/remotes/" + ORIGIN_MAIN);

        assertEquals(ORIGIN_MAIN, branch.displayName(), "remotes ref prefix should be stripped");
    }

    @Test
    void displayNameLeavesUnprefixedNames() {
        final Branch branch = new Branch(ORIGIN_MAIN);

        assertEquals(
                ORIGIN_MAIN,
                branch.displayName(),
                "names without known prefixes should be unchanged");
    }

    @Test
    void withoutHeadsPrefixStripsHeadsPrefix() {
        final Branch branch = new Branch("refs/heads/main");

        assertEquals("main", branch.withoutHeadsPrefix(), "heads ref prefix should be stripped");
    }

    @Test
    void withoutHeadsPrefixLeavesOtherNames() {
        final Branch branch = new Branch("refs/remotes/" + ORIGIN_MAIN);

        assertEquals(
                "refs/remotes/" + ORIGIN_MAIN,
                branch.withoutHeadsPrefix(),
                "non-head refs should be unchanged");
    }
}
