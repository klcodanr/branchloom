package com.jagent.desktop.api;

/** A user-triggered application workflow. */
public interface Action {
    String id();

    String label();

    default boolean enabled() {
        return true;
    }

    void execute();
}
