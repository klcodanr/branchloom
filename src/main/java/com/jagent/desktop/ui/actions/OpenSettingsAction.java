package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.EditorCommands;
import com.jagent.desktop.services.dialog.DialogService;
import java.nio.file.Path;
import javax.annotation.Nullable;

public final class OpenSettingsAction implements Action {
    private final AppState appState;
    private final Path dataDirectory;
    private final DialogService dialogService;

    @Inject
    public OpenSettingsAction(
            final AppState appState,
            final @Named("dataDirectory") @Nullable Path dataDirectory,
            final DialogService dialogService) {
        this.appState = appState;
        this.dialogService = dialogService;
        this.dataDirectory = dataDirectory;
    }

    @Override
    public String id() {
        return "settings";
    }

    @Override
    public String label() {
        return "Open Settings";
    }

    @Override
    public void execute() {
        if (dataDirectory == null) {
            dialogService.errorMessage("Data directory not configured", "Could not open settings");

            return;
        }
        final Path settings = dataDirectory.resolve("settings.json");
        final var editors = appState.appSettings().tools();
        if (editors.isEmpty()) {
            dialogService.errorMessage("No editor configured", "Could not open settings");
            return;
        }
        final var editor = editors.getFirst();
        BackgroundOperations.runCommand(
                "Settings",
                "Open Settings",
                EditorCommands.openFile(editor, settings, 0, 0),
                dataDirectory,
                null);
    }
}
