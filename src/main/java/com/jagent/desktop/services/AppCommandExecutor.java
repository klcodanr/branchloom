package com.jagent.desktop.services;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Executes commands using the selected project and session context. */
public final class AppCommandExecutor {
    private final Supplier<Project> project;
    private final Supplier<Session> session;
    private final BiConsumer<String, String> failure;

    public AppCommandExecutor(
            final Supplier<Project> project,
            final Supplier<Session> session,
            final BiConsumer<String, String> failure) {
        this.project = project;
        this.session = session;
        this.failure = failure;
    }

    public void run(final String command, final Path directory) {
        run(command, directory, null, null);
    }

    public void run(final String command, final Path directory, final Runnable after) {
        run(command, directory, after, null);
    }

    public void run(
            final String command,
            final Path directory,
            final Runnable after,
            final Runnable onFailure) {
        CommandRunner.run(
                Template.expand(command, project.get(), session.get(), true),
                directory,
                after,
                output -> {
                    failure.accept("Command failed", output);
                    if (onFailure != null) {
                        onFailure.run();
                    }
                });
    }
}
