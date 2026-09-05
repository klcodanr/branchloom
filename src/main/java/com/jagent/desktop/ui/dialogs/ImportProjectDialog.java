package com.jagent.desktop.ui.dialogs;

import static com.jagent.desktop.ui.components.UiFactory.button;
import static com.jagent.desktop.ui.components.UiFactory.form;

import com.jagent.desktop.models.ActionContext;
import java.awt.BorderLayout;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Collects and validates the details needed to import a remote Git project. */
public final class ImportProjectDialog extends JDialog {
    private static final String TITLE = "Clone remote project";

    private final Consumer<Request> onValid;
    private final JTextField remote = new JTextField(35);
    private final JTextField destination = new JTextField(35);
    private final JButton cancel = new JButton("Cancel");
    private final JButton ok = new JButton("OK");

    public record Request(String remote, Path destination) {}

    public ImportProjectDialog(final ActionContext actionContext, final Consumer<Request> onValid) {
        super(actionContext.window(), TITLE, ModalityType.APPLICATION_MODAL);
        this.onValid = onValid;
        setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy());
        remote.setName("import-remote");
        destination.setName("import-destination");
        cancel.setName("import-cancel");
        ok.setName("import-ok");

        final JButton browse = button("Browse...");
        browse.addActionListener(event -> chooseDestination(actionContext));
        final JPanel destinationInput = new JPanel(new BorderLayout(8, 0));
        destinationInput.add(destination, BorderLayout.CENTER);
        destinationInput.add(browse, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(form("Git remote URL", remote, "Destination directory", destinationInput));
        final JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(ok);
        add(buttons, BorderLayout.SOUTH);
        cancel.addActionListener(event -> dispose());
        ok.addActionListener(event -> validateAndSubmit());
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(actionContext.window());
    }

    private void chooseDestination(final ActionContext actionContext) {
        final JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
        chooser.setDialogTitle("Select clone destination");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setApproveButtonText("Select Folder");
        if (chooser.showOpenDialog(actionContext.window()) == JFileChooser.APPROVE_OPTION) {
            destination.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void validateAndSubmit() {
        final String failure = validationFailure(remote.getText(), destination.getText());
        if (failure != null) {
            showError(failure);
            return;
        }
        final Path path = Path.of(destination.getText().trim()).toAbsolutePath().normalize();
        dispose();
        onValid.accept(new Request(remote.getText().trim(), path));
    }

    @SuppressWarnings({"PMD.CommentDefaultAccessModifier", "PMD.CyclomaticComplexity"})
    static String validationFailure( // default access
            final String remoteText, final String destinationText) {
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

    private void showError(final String message) {
        JOptionPane.showMessageDialog(this, message, TITLE, JOptionPane.ERROR_MESSAGE);
    }
}
