package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionMetadataTest {
    private static final String ASSERTION_MESSAGE = "action metadata should be valid";

    @Test
    void actionMetadataIsStable() {
        final AppState state = TestAppState.empty();
        final var context =
                new ActionContext(TestAppState.controller(state), state, null, null, null);
        final List<Action> actions =
                List.of(
                        new CreateProjectAction(context),
                        new FindAction(context),
                        new AboutAction(context),
                        new ShortcutsAction(context),
                        new OpenSettingsAction(context),
                        new ProblemsAction(context),
                        new ResourceUsageAction(context),
                        new CopyPathAction(context),
                        new CopyBranchAction(context),
                        new RunCommandAction(context, "Run", "printf test"),
                        new OpenDirectoryAction(context),
                        new ImportBranchAction(context),
                        new ImportWorktreeAction(context),
                        new RenameSessionAction(context),
                        new RemoveProjectAction(context),
                        new RemoveSessionAction(context),
                        new BulkCreateSessionsAction(context),
                        new PasteSessionsAction(context),
                        new CreateSessionAction(context),
                        new UpdateBranchAction(context),
                        new RefreshSettingsAction(context),
                        new OpenProjectSettingsAction(context),
                        new OpenSessionAction(context),
                        new OpenProjectAction(context),
                        new CreateTerminalAction(context, "Terminal", "sh"));

        for (final Action action : actions) {
            assertFalse(action.id().isBlank(), ASSERTION_MESSAGE);
            assertFalse(action.label().isBlank(), ASSERTION_MESSAGE);
        }
        assertEquals("run-command-run", actions.get(9).id(), ASSERTION_MESSAGE);
    }
}
