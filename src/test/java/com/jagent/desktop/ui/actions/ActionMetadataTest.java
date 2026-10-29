package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionMetadataTest {
    private static final String ASSERTION_MESSAGE = "action metadata should be populated";

    @Test
    void actionMetadataIsStable() {
        final AppState state = TestAppState.empty();
        final var controller = TestAppState.controller(state);
        final var dialogs = TestAppState.dialogService();
        final List<Action> actions =
                List.of(
                        new CreateProjectAction(controller, state, dialogs),
                        new FindAction(controller, dialogs),
                        new AboutAction(dialogs),
                        new ShortcutsAction(dialogs),
                        new OpenSettingsAction(state, null, dialogs),
                        new ProblemsAction(controller),
                        new ResourceUsageAction(controller),
                        new CopyPathAction(controller, state),
                        new CopyBranchAction(controller, state),
                        new RunCommandAction(controller, state, dialogs, "Run", "printf test"),
                        new OpenDirectoryAction(state, controller),
                        new ImportBranchAction(controller, state, dialogs),
                        new ImportWorktreeAction(controller, state, dialogs),
                        new RenameSessionAction(controller, state, dialogs),
                        new RemoveProjectAction(controller, state, dialogs),
                        new RemoveSessionAction(
                                TestAppState.backgroundJobs(state), controller, state, dialogs),
                        new BulkCreateSessionsAction(
                                controller, state, dialogs, TestAppState.bulkSessionCreator(state)),
                        new PasteSessionsAction(
                                controller, state, dialogs, TestAppState.bulkSessionCreator(state)),
                        new CreateSessionAction(
                                controller, state, TestAppState.backgroundJobs(state), dialogs),
                        new UpdateBranchAction(controller, dialogs),
                        new RefreshSettingsAction(null, dialogs),
                        new OpenProjectSettingsAction(controller),
                        new OpenSessionAction(controller),
                        new OpenProjectAction(controller),
                        new CreateTerminalAction(controller, state, "Terminal", "sh"));

        for (final Action action : actions) {
            assertFalse(action.id().isBlank(), ASSERTION_MESSAGE);
            assertFalse(action.label().isBlank(), ASSERTION_MESSAGE);
        }
        assertEquals("run-command-run", actions.get(9).id(), ASSERTION_MESSAGE);
    }
}
