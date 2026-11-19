package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.BaseButton;
import com.jagent.desktop.ui.components.FormPanel;
import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Collects the location, name, and first agent prompt for a new project. */
public final class NewProjectDialog extends FormDialog {
    public static final String TITLE = "Create new project";

    private final JTextField name = new JTextField(32);
    private final JTextField folder = new JTextField(32);
    private final JTextArea prompt = new JTextArea(5, 32);
    private final JComboBox<Agent> agent;

    public record Request(String name, Path folder, Agent agent, String prompt) {}

    public NewProjectDialog(
            final DialogService dialogs, final List<Agent> agents, final Consumer<Request> submit) {
        super();
        agent = new JComboBox<>(agents.toArray(Agent[]::new));
        name.setName("new-project-name");
        folder.setName("new-project-folder");
        agent.setName("new-project-agent");
        prompt.setName("new-project-prompt");
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);

        final var browse = new BaseButton("Choose parent...");
        browse.addActionListener(event -> chooseParent());
        final var folderInput = new JPanel(new BorderLayout(8, 0));
        folderInput.add(folder, BorderLayout.CENTER);
        folderInput.add(browse, BorderLayout.EAST);
        initialize(
                new FormPanel(
                        "Project name", name,
                        "New folder path", folderInput,
                        "Agent", agent,
                        "Initial prompt", new JScrollPane(prompt)),
                "Create",
                "new-project-cancel",
                "new-project-create",
                () -> validateAndSubmit(submit),
                dialogs);
    }

    private void chooseParent() {
        final Path parent =
                dialogService.chooseDirectory(
                        "Select the parent of the new folder",
                        Path.of(System.getProperty("user.home")),
                        "Select Folder");
        if (parent != null) {
            final String prefix = parent.toAbsolutePath().toString();
            folder.setText(prefix.endsWith(File.separator) ? prefix : prefix + File.separator);
            folder.requestFocusInWindow();
        }
    }

    private void validateAndSubmit(final Consumer<Request> submit) {
        if (name.getText().isBlank() || folder.getText().isBlank() || prompt.getText().isBlank()) {
            dialogService.errorMessage(
                    "Enter a project name, new folder path, and initial prompt.", TITLE);
            return;
        }
        final Path destination;
        try {
            destination = Path.of(folder.getText().strip()).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            dialogService.errorMessage("Enter a valid path for the new folder.", TITLE);
            return;
        }
        complete();
        submit.accept(
                new Request(
                        name.getText().strip(),
                        destination,
                        (Agent) agent.getSelectedItem(),
                        prompt.getText()));
    }
}
