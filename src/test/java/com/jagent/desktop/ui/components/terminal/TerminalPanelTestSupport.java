package com.jagent.desktop.ui.components.terminal;

import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import java.lang.reflect.Constructor;
import java.util.function.Consumer;

public final class TerminalPanelTestSupport {
    private TerminalPanelTestSupport() {}

    public static TerminalPanel create(
            final TerminalRuntime runtime,
            final Consumer<TerminalState> stateChanged,
            final DialogService dialogs) {
        try {
            final Constructor<TerminalPanel> constructor =
                    TerminalPanel.class.getDeclaredConstructor(
                            TerminalRuntime.class,
                            TerminalId.class,
                            Consumer.class,
                            DialogService.class);
            constructor.trySetAccessible();
            return constructor.newInstance(runtime, TerminalId.create(), stateChanged, dialogs);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not construct terminal panel for test", exception);
        }
    }

    public static void title(final TerminalPanel panel, final String title) {
        try {
            final var method =
                    TerminalPanel.class.getDeclaredMethod("applicationTitleChanged", String.class);
            method.trySetAccessible();
            method.invoke(panel, title);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not send terminal title for test", exception);
        }
    }
}
