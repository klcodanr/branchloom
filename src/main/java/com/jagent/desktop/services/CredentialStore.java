package com.jagent.desktop.services;

import java.util.Optional;

/** Stores application secrets without putting their values in application state. */
public interface CredentialStore {
    void put(String key, String secret);

    Optional<String> get(String key);

    void delete(String key);
}
