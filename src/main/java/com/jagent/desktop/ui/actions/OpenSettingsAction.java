package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.EditorCommands;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import java.nio.file.Path;

public final class OpenSettingsAction extends BaseAction {
    public OpenSettingsAction(final ActionContext actionContext) {
        super(actionContext);
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
        final Path settings = actionContext.dataDirectory().resolve("settings.json");
        final var editors = actionContext.appState().appSettings().tools();
        if (editors.isEmpty()) {
            ErrorDialogs.show(
                    actionContext.window(),
                    "Could not open settings",
                    "No editor is configured for settings.json.");
            return;
        }
        final var editor = editors.getFirst();
        final Path parent = settings.getParent();
        if (parent == null) {
            ErrorDialogs.show(
                    actionContext.window(),
                    "Could not open settings",
                    "Could not determine the settings directory.");
            return;
        }
        RunCommandAction.run(
                EditorCommands.openFile(editor, settings, 0, 0),
                parent.toString(),
                editor.label(),
                actionContext.window());
    }
}
