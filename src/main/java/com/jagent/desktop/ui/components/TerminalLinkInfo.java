package com.jagent.desktop.ui.components;

import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import java.util.function.BooleanSupplier;

/** Link metadata retained so terminal menus can offer link-specific actions. */
public final class TerminalLinkInfo extends LinkInfo {
    private final String value;

    public TerminalLinkInfo(
            final String value, final BooleanSupplier activationAllowed, final Runnable navigate) {
        super(
                () -> {
                    if (activationAllowed.getAsBoolean()) {
                        navigate.run();
                    }
                });
        this.value = value;
    }

    public String value() {
        return value;
    }
}
