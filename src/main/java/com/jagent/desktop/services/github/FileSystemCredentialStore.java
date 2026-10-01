package com.jagent.desktop.services.github;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FileSystemCredentialStore implements CredentialStore {
    private final Map<String, String> secrets = new HashMap<>();

    @Override
    public void put(final String key, final String secret) {
        secrets.put(key, secret);
    }

    @Override
    public Optional<String> get(final String key) {
        return Optional.ofNullable(secrets.get(key));
    }

    @Override
    public void delete(final String key) {
        secrets.remove(key);
    }
}
