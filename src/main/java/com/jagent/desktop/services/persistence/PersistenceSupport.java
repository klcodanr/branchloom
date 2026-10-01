package com.jagent.desktop.services.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.models.github.PatCredential;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

class PersistenceSupport {
    protected static final Gson JSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(Credential.class, new CredentialJsonAdapter())
                    .create();
    protected static final Path DEFAULT_DIRECTORY =
            Path.of(System.getProperty("user.home"), ".branchloom");

    protected PersistenceSupport() {}

    protected void writeAtomically(final Path path, final Object value) throws IOException {
        final Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(value));
        Files.move(
                temporary,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static final class CredentialJsonAdapter
            implements JsonSerializer<Credential>, JsonDeserializer<Credential> {
        private static final String TYPE = "type";
        private static final String TYPE_PAT = "pat";
        private static final String TYPE_CLI = "cli";
        private static final String FIELD_CREDENTIAL_ID = "credentialId";
        private static final String FIELD_ID = "id";
        private static final String FIELD_HOST = "host";
        private static final String FIELD_NAME = "name";
        private static final String FIELD_USER = "user";

        @Override
        public JsonElement serialize(
                final Credential source,
                final Type sourceType,
                final JsonSerializationContext context) {
            if (source == null) {
                return null;
            }
            final JsonObject json = new JsonObject();
            if (source instanceof PatCredential pat) {
                json.addProperty(TYPE, TYPE_PAT);
                json.addProperty(FIELD_CREDENTIAL_ID, pat.credentialId());
                json.addProperty(FIELD_HOST, pat.host());
                json.addProperty(FIELD_NAME, pat.name());
                return json;
            }
            if (source instanceof CliCredential cli) {
                json.addProperty(TYPE, TYPE_CLI);
                json.addProperty(FIELD_HOST, cli.host());
                json.addProperty(FIELD_NAME, cli.name());
                json.addProperty(FIELD_USER, cli.user());
                return json;
            }
            json.addProperty(FIELD_ID, source.id());
            json.addProperty(FIELD_HOST, source.host());
            json.addProperty(FIELD_NAME, source.name());
            return json;
        }

        @Override
        public Credential deserialize(
                final JsonElement source,
                final Type sourceType,
                final JsonDeserializationContext context) {
            if (source == null || source.isJsonNull()) {
                return null;
            }
            final JsonObject json = source.getAsJsonObject();
            final String type = stringValue(json, TYPE);
            if (TYPE_PAT.equals(type) || json.has(FIELD_CREDENTIAL_ID)) {
                return new PatCredential(
                        stringValue(json, FIELD_CREDENTIAL_ID),
                        stringValue(json, FIELD_HOST),
                        stringValue(json, FIELD_NAME));
            }
            if (TYPE_CLI.equals(type) || json.has(FIELD_USER)) {
                return new CliCredential(
                        stringValue(json, FIELD_HOST),
                        stringValue(json, FIELD_NAME),
                        stringValue(json, FIELD_USER));
            }
            return null;
        }

        private static String stringValue(final JsonObject json, final String field) {
            final JsonElement value = json.get(field);
            if (value == null || value.isJsonNull()) {
                return null;
            }
            return value.getAsString();
        }
    }
}
