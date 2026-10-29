package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.dialog.DialogService;

public final class RefreshSettingsAction implements Action {
    private final Runnable refreshSettings;
    private final DialogService dialogService;

    @Inject
    public RefreshSettingsAction(
            final @Named("refreshSettings") Runnable refreshSettings,
            final DialogService dialogService) {
        this.refreshSettings = refreshSettings;
        this.dialogService = dialogService;
    }

    @Override
    public String id() {
        return "refresh-settings";
    }

    @Override
    public String label() {
        return "Refresh Settings";
    }

    @Override
    public void execute() {
        try {
            refreshSettings.run();
        } catch (IllegalStateException exception) {
            dialogService.errorMessage(exception.getMessage(), "Settings could not be refreshed");
        }
    }
}
