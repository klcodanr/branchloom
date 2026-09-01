package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AgentDetectionTest {
    @Test
    void detectedAgentsHaveUniqueNamesAndCompleteCommands() {
        final var agents = AgentDetection.detect();

        assertEquals(
                agents.stream().map(agent -> agent.name).distinct().count(),
                agents.size(),
                "detected agent names should be unique");
        agents.forEach(
                agent -> {
                    assertNotNull(agent.name, "detected agents should have names");
                    assertFalse(agent.name.isBlank(), "detected agent names should not be blank");
                    assertFalse(
                            agent.newSessionCommand.isBlank(),
                            "detected agents should have session commands");
                    assertFalse(
                            agent.openCommand.isBlank(),
                            "detected agents should have open commands");
                });
    }
}
