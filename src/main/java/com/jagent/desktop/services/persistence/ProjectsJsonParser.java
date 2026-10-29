package com.jagent.desktop.services.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jagent.desktop.models.IdType;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Converts the projects persistence format to and from application models. */
public final class ProjectsJsonParser extends PersistenceSupport {
    public PersistedProjects parse(final String jsonText) {
        final JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();
        final PersistedProjects projects = new PersistedProjects();
        json.getAsJsonObject("projects")
                .entrySet()
                .forEach(
                        entry ->
                                projects.projects.put(
                                        entry.getKey(),
                                        JSON.fromJson(withId(entry), Project.class)));
        json.getAsJsonObject("sessions")
                .entrySet()
                .forEach(
                        entry ->
                                projects.sessions.put(
                                        entry.getKey(),
                                        JSON.fromJson(withId(entry), Session.class)));
        json.getAsJsonObject("terminals")
                .entrySet()
                .forEach(
                        entry -> {
                            final JsonObject terminalJson = withId(entry);
                            final IdType owner = owner(terminalJson, projects.sessions);
                            projects.terminals.put(
                                    entry.getKey(),
                                    new Terminal(
                                            new TerminalId(UUID.fromString(entry.getKey())),
                                            owner,
                                            terminalJson.get("title").getAsString(),
                                            terminalJson.get("command").getAsString()));
                        });
        return projects;
    }

    public String serialize(final PersistedProjects projects) {
        return JSON.toJson(projects);
    }

    private static JsonObject withId(final Map.Entry<String, JsonElement> entry) {
        final JsonObject json = entry.getValue().getAsJsonObject();
        if (!json.has("id") || json.get("id").isJsonNull()) {
            final JsonObject id = new JsonObject();
            id.addProperty("value", entry.getKey());
            json.add("id", id);
        }
        return json;
    }

    private static IdType owner(
            final JsonObject terminalJson, final Map<String, Session> sessions) {
        final JsonObject ownerJson = terminalJson.getAsJsonObject("owner");
        if (ownerJson != null) {
            final UUID ownerId = UUID.fromString(ownerJson.get("value").getAsString());
            final String type = ownerJson.has("type") ? ownerJson.get("type").getAsString() : "";
            if (type.endsWith("SessionId")) {
                return new SessionId(ownerId);
            }
            if (type.endsWith("ProjectId")) {
                return new ProjectId(ownerId);
            }
            return sessions.containsKey(ownerId.toString())
                    ? new SessionId(ownerId)
                    : new ProjectId(ownerId);
        }
        final JsonElement sessionId = terminalJson.get("sessionId");
        if (sessionId != null && !sessionId.isJsonNull()) {
            return new SessionId(UUID.fromString(idValue(sessionId)));
        }
        return new ProjectId(UUID.fromString(idValue(terminalJson.get("projectId"))));
    }

    private static String idValue(final JsonElement id) {
        return id.getAsJsonObject().get("value").getAsString();
    }

    public static final class PersistedProjects {
        private final Map<String, Project> projects = new LinkedHashMap<>();
        private final Map<String, Session> sessions = new LinkedHashMap<>();
        private final Map<String, Terminal> terminals = new LinkedHashMap<>();

        public Map<String, Project> projects() {
            return projects;
        }

        public Map<String, Session> sessions() {
            return sessions;
        }

        public Map<String, Terminal> terminals() {
            return terminals;
        }
    }
}
