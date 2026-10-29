package com.jagent.desktop.ui.dialogs;

import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.BaseButton;
import com.jagent.desktop.ui.components.FormPanel;
import com.jagent.desktop.ui.components.GitHubAuthSelector;
import java.awt.BorderLayout;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Collects and validates the details needed to import a remote Git project. */
public final class ImportProjectDialog extends FormDialog {
    private static final String TITLE = "Clone remote project";

    private final Consumer<Request> onValid;
    private final JTextField remote = new JTextField(35);
    private final JTextField destination = new JTextField(35);
    private final JComboBox<Credential> githubAuth;

    public record Request(String remote, Path destination, Credential auth) {}

    public ImportProjectDialog(
            final DialogService dialogService,
            final List<Credential> configuredAuths,
            final Consumer<Request> onValid) {
        super();
        this.onValid = onValid;
        githubAuth = GitHubAuthSelector.renderConfigured(configuredAuths);
        setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy());
        remote.setName("import-remote");
        destination.setName("import-destination");

        final JButton browse = new BaseButton("Browse...");
        browse.addActionListener(event -> chooseDestination(dialogService));
        final JPanel destinationInput = new JPanel(new BorderLayout(8, 0));
        destinationInput.add(destination, BorderLayout.CENTER);
        destinationInput.add(browse, BorderLayout.EAST);

        initialize(
                new FormPanel(
                        "Git remote URL",
                        remote,
                        "Destination directory",
                        destinationInput,
                        "GitHub connection",
                        githubAuth),
                "OK",
                "import-cancel",
                "import-ok",
                this::validateAndSubmit,
                dialogService);
    }

    private void chooseDestination(final DialogService dialogService) {
        final Path selected =
                dialogService.chooseDirectory(
                        "Select clone destination",
                        Path.of(initialDirectory(destination.getText())),
                        "Select Folder");
        if (selected != null) {
            destination.setText(selected.toAbsolutePath().toString());
        }
    }

    protected static String initialDirectory(final String folderPath) {
        final String trimmedPath = folderPath.trim();
        return trimmedPath.isBlank() ? System.getProperty("user.home") : trimmedPath;
    }

    private void validateAndSubmit() {
        final String failure = validationFailure(remote.getText(), destination.getText());
        if (failure != null) {
            dialogService.message(failure, TITLE, DialogService.MessageType.ERROR);
            return;
        }
        final Path path = Path.of(destination.getText().trim()).toAbsolutePath().normalize();
        onValid.accept(
                new Request(
                        remote.getText().trim(), path, (Credential) githubAuth.getSelectedItem()));
    }

    @SuppressWarnings("PMD.CyclomaticComplexity")
    private static String validationFailure(final String remoteText, final String destinationText) {
        if (remoteText.isBlank()) {
            return "Enter a Git remote URL.";
        }
        if (destinationText.isBlank()) {
            return "Choose a destination directory.";
        }

        final Path path = Path.of(destinationText.trim()).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) {
                return "The destination must be a directory.";
            }
            try (var entries = Files.list(path)) {
                if (entries.findAny().isPresent()) {
                    return "The destination directory must be empty.";
                }
            } catch (IOException exception) {
                return "The destination directory cannot be read: " + exception.getMessage();
            }
        } else {
            final Path parent = path.getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                return "The destination's parent directory must already exist.";
            }
        }
        return path.getFileName() == null
                ? "Choose a destination directory below the filesystem root."
                : null;
    }
}
