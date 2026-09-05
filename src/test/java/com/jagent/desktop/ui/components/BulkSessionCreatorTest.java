package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BulkSessionCreatorTest {
    @Test
    void uniqueNamesAreSluggedAndSuffixedAgainstExistingNames() {
        final Set<String> names = new HashSet<>(Set.of("fix-login", "fix-login-2"));

        final String name =
                BulkSessionCreator.uniqueName(
                        new BulkSessionCreator.Candidate("Fix login", "Issue", "prompt"), names);

        assertEquals("fix-login-3", name, "name should be suffixed past all collisions");
    }

    @Test
    void uniqueNamesTreatExistingNamesCaseInsensitively() {
        final Set<String> names = new HashSet<>(Set.of("FIX-LOGIN"));

        assertEquals(
                "fix-login-2",
                BulkSessionCreator.uniqueName(
                        new BulkSessionCreator.Candidate("Fix login", "Issue", "prompt"), names),
                "name collision checks should ignore case");
    }
}
