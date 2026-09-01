package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TerminalResourcesTest {
    @Test
    void samplesLiveRootsAndIgnoresMissingProcesses() {
        final long currentPid = ProcessHandle.current().pid();

        final var sample =
                TerminalResources.sample(
                        List.of(
                                new TerminalResources.ProcessTarget("current", currentPid),
                                new TerminalResources.ProcessTarget("missing", -1)));

        assertEquals(1, sample.terminals().size(), "assertion values should match");
        assertEquals(
                "current", sample.terminals().getFirst().name(), "assertion values should match");
        assertEquals(
                currentPid, sample.terminals().getFirst().pid(), "assertion values should match");
        assertTrue(
                sample.terminals().getFirst().processCount() >= 1,
                "assertion condition should hold");
    }

    @Test
    void emptyRootsProduceEmptySample() {
        final var sample = TerminalResources.sample(List.of());

        assertTrue(sample.terminals().isEmpty(), "assertion condition should hold");
        assertTrue(!sample.memoryAvailable(), "assertion condition should hold");
    }
}
