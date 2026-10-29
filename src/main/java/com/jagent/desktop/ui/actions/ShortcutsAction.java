package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.dialog.DialogService;

public final class ShortcutsAction implements Action {
    private final DialogService dialogService;

    @Inject
    public ShortcutsAction(final DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @Override
    public String id() {
        return "shortcuts";
    }

    @Override
    public String label() {
        return "Keyboard shortcuts";
    }

    @Override
    public void execute() {

        dialogService.message(
                "Find: Cmd/Ctrl+F\nCommand palette: Cmd/Ctrl+K\nNew session: Cmd/Ctrl+N\n"
                        + "Add local project: Cmd/Ctrl+Shift+N\nNew terminal: Cmd/Ctrl+T\n"
                        + "Close terminal: Cmd/Ctrl+W\n"
                        + "Rename terminal: Cmd/Ctrl+Shift+R\nTerminal 1-9: Cmd/Ctrl+1-9",
                "Keyboard shortcuts",
                DialogService.MessageType.INFORMATION);
    }
}
