package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import javax.swing.JOptionPane;

public final class ShortcutsAction extends BaseAction {

    public ShortcutsAction(final ActionContext actionContext) {
        super(actionContext);
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

        JOptionPane.showMessageDialog(
                actionContext.window(),
                "Find: Cmd/Ctrl+F\nCommand palette: Cmd/Ctrl+K\nNew session: Cmd/Ctrl+N\n"
                        + "Add local project: Cmd/Ctrl+Shift+N\nNew terminal: Cmd/Ctrl+T\n"
                        + "Close terminal: Cmd/Ctrl+W\n"
                        + "Rename terminal: Cmd/Ctrl+Shift+R\nTerminal 1-9: Cmd/Ctrl+1-9",
                "Keyboard shortcuts",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
