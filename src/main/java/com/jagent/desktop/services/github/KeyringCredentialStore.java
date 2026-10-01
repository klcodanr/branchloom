package com.jagent.desktop.services.github;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import java.util.Optional;

/** Credential store backed by the operating system keyring. */
public final class KeyringCredentialStore implements CredentialStore, AutoCloseable {
    private static final String SERVICE = "Branchloom";

    private final Keyring keyring;

    public KeyringCredentialStore() {
        this(createKeyring());
    }

    @SuppressWarnings("PMD.CommentDefaultAccessModifier")
    KeyringCredentialStore(final Keyring keyring) {
        this.keyring = keyring;
    }

    @Override
    public void put(final String key, final String secret) {
        try {
            keyring.setPassword(SERVICE, key, secret);
        } catch (PasswordAccessException exception) {
            throw new IllegalStateException(
                    "Could not store credential in the OS keyring", exception);
        }
    }

    @Override
    public Optional<String> get(final String key) {
        try {
            return Optional.ofNullable(keyring.getPassword(SERVICE, key));
        } catch (PasswordAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(final String key) {
        try {
            keyring.deletePassword(SERVICE, key);
        } catch (PasswordAccessException ignored) {
            // Deleting a missing or inaccessible credential is idempotent here.
        }
    }

    @Override
    public void close() {
        try {
            keyring.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not close the OS keyring", exception);
        }
    }

    private static Keyring createKeyring() {
        try {
            return Keyring.create();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize the OS keyring", exception);
        }
    }
}
