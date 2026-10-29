package com.jagent.desktop.services;

import com.google.inject.Inject;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.BackgroundJobs.Handle;
import com.jagent.desktop.services.SessionCreationService.CreatedSession;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.utils.ErrorMessages;
import com.jagent.desktop.ui.utils.GitUtils;
import com.jagent.desktop.ui.utils.SessionNames;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

/** Runs the shared multi-session creation workflow for imported candidates. */
public final class BulkSessionCreator {
    private final BackgroundJobs backgroundJobs;
    private final AppState appState;
    private final DialogService dialogService;
    private final SessionCreationService sessionCreator;
    private final SessionLauncher sessionLauncher;

    @Inject
    public BulkSessionCreator(
            final BackgroundJobs backgroundJobs,
            final SessionCreationService sessionCreator,
            final SessionLauncher sessionLauncher,
            final AppState appState,
            final DialogService dialogService) {
        this.backgroundJobs = backgroundJobs;
        this.appState = appState;
        this.dialogService = dialogService;
        this.sessionCreator = sessionCreator;
        this.sessionLauncher = sessionLauncher;
    }

    public void create(
            final ProjectId projectId,
            final Project project,
            final Agent agent,
            final List<Candidate> candidates,
            final String title) {
        final Handle job = backgroundJobs.start(title, project.name(), "Multiple sessions");
        BackgroundOperations.submit(
                        "Sessions",
                        "bulk-create",
                        () -> createAll(projectId, project, agent, candidates, job))
                .whenCompleteAsync(
                        (result, failure) -> {
                            if (failure != null) {
                                job.fail(
                                        ErrorMessages.deepestCause(
                                                failure, "Bulk session creation failed."));
                            } else {
                                job.complete();
                                showResult(title, result);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private Result createAll(
            final ProjectId projectId,
            final Project project,
            final Agent agent,
            final List<Candidate> candidates,
            final Handle job) {
        final Set<String> names = SessionNames.existing(appState, project);
        final List<String> successes = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final AtomicInteger completed = new AtomicInteger();
        for (final Candidate candidate : candidates) {
            final String name = uniqueName(candidate, names);
            try {
                job.update(
                        "Creating "
                                + completed.incrementAndGet()
                                + " of "
                                + candidates.size()
                                + ": "
                                + name);
                final CreatedSession created =
                        sessionCreator.create(
                                projectId, project, agent, name, candidate.prompt(), null);
                SwingUtilities.invokeLater(() -> sessionLauncher.launch(project, created));
                names.add(name.toLowerCase(Locale.ROOT));
                successes.add(candidate.label() + " -> " + name);
            } catch (RuntimeException | IOException exception) {
                failures.add(
                        candidate.label()
                                + ": "
                                + ErrorMessages.deepestCause(exception, "Creation failed."));
            }
        }
        return new Result(successes, failures);
    }

    public static String uniqueName(
            final Candidate candidate, final Set<String> names) { // default access
        return SessionNames.unique(GitUtils.toBranchSlug(candidate.name()), names);
    }

    private void showResult(final String title, final Result result) {
        final StringBuilder text = new StringBuilder(64);
        text.append("Created ").append(result.successes().size()).append(" session(s).");
        if (!result.failures().isEmpty()) {
            text.append("\n\nFailed:\n").append(String.join("\n", result.failures()));
        }
        dialogService.message(
                text.toString(),
                title,
                result.failures().isEmpty()
                        ? DialogService.MessageType.INFORMATION
                        : DialogService.MessageType.WARNING);
    }

    public record Candidate(String name, String label, String prompt) {}

    private record Result(List<String> successes, List<String> failures) {}
}
