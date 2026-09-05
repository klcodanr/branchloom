package com.jagent.desktop.test;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.Defaults;
import java.util.Map;

/** Common application-state fixtures for action and view tests. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class TestAppState {
    private TestAppState() {}

    public static AppState empty() {
        return new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
    }
}
