package com.jagent.desktop.test;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.awt.Window;
import java.util.Map;

/** Common application-state fixtures for action and view tests. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class TestAppState {
    private TestAppState() {}

    public static AppState empty() {
        return new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
    }

    public static ActionContext context(final AppState state) {
        return new ActionContext(new ViewCoordinator(state), state, (Window) null);
    }
}
