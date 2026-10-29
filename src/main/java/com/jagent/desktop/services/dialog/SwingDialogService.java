package com.jagent.desktop.services.dialog;

import com.google.inject.Inject;
import java.awt.Dialog;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/** Swing-backed dialog implementation used by the desktop application. */
public final class SwingDialogService implements DialogService {
    private final Window owner;

    @Inject
    public SwingDialogService(final Window owner) {
        this.owner = owner;
    }

    private int toSwingMessage(final DialogService.MessageType messageType) {
        switch (messageType) {
            case ERROR:
                return JOptionPane.ERROR_MESSAGE;
            case INFORMATION:
                return JOptionPane.INFORMATION_MESSAGE;
            case WARNING:
                return JOptionPane.WARNING_MESSAGE;
            case QUESTION:
                return JOptionPane.QUESTION_MESSAGE;
            case PLAIN:
                return JOptionPane.PLAIN_MESSAGE;
        }
        throw new IllegalArgumentException("Unsupported message type: " + messageType);
    }

    private int toSwingOption(final DialogService.OptionType optionType) {
        switch (optionType) {
            case YES_NO:
                return JOptionPane.YES_NO_OPTION;
            case OK_CANCEL:
                return JOptionPane.OK_CANCEL_OPTION;
        }
        throw new IllegalArgumentException("Unsupported option type: " + optionType);
    }

    private DialogService.ReturnType fromSwingReturnType(final int returnType) {
        switch (returnType) {
            case JOptionPane.OK_OPTION:
                return DialogService.ReturnType.OK;
            case JOptionPane.CANCEL_OPTION:
                return DialogService.ReturnType.CANCEL;
            case JOptionPane.NO_OPTION:
                return DialogService.ReturnType.NO;
            default:
                return DialogService.ReturnType.CLOSED;
        }
    }

    @Override
    public void message(
            final String message, final String title, final DialogService.MessageType messageType) {
        JOptionPane.showMessageDialog(owner, message, title, toSwingMessage(messageType));
    }

    @Override
    public void errorMessage(final String message, final String title) {
        this.message(message, title, DialogService.MessageType.ERROR);
    }

    @Override
    public DialogService.ReturnType confirm(
            final Object content,
            final String title,
            final DialogService.OptionType optionType,
            final DialogService.MessageType messageType) {
        return fromSwingReturnType(
                JOptionPane.showConfirmDialog(
                        owner,
                        content,
                        title,
                        toSwingOption(optionType),
                        toSwingMessage(messageType)));
    }

    @Override
    public Object input(
            final Object message, final String title, final DialogService.MessageType messageType) {
        return JOptionPane.showInputDialog(
                owner, message, title, toSwingMessage(messageType), null, null, null);
    }

    @Override
    public DialogService.ReturnType option(
            final Object message,
            final String title,
            final DialogService.MessageType messageType,
            final Object[] options,
            final Object initialValue) {
        return fromSwingReturnType(
                JOptionPane.showOptionDialog(
                        owner,
                        message,
                        title,
                        JOptionPane.DEFAULT_OPTION,
                        toSwingMessage(messageType),
                        null,
                        options,
                        initialValue));
    }

    @Override
    public Path chooseDirectory(
            final String title, final Path initialDirectory, final String approveButtonText) {
        final JFileChooser chooser = new JFileChooser(initialDirectory.toFile());
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setApproveButtonText(approveButtonText);
        chooser.setMultiSelectionEnabled(false);
        return chooser.showOpenDialog(this.owner) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath()
                : null;
    }

    @Override
    public DialogService.ReturnType show(
            final String title, final JComponent content, final String primaryLabel) {
        final JOptionPane pane =
                new JOptionPane(
                        content,
                        JOptionPane.PLAIN_MESSAGE,
                        JOptionPane.OK_CANCEL_OPTION,
                        null,
                        new Object[] {primaryLabel, "Cancel"},
                        primaryLabel);
        final JDialog dialog = pane.createDialog(owner, title);
        final PropertyChangeListener cancellation =
                event -> {
                    if (("dialog.cancelled".equals(event.getPropertyName())
                                    || "dialog.completed".equals(event.getPropertyName()))
                            && Boolean.TRUE.equals(event.getNewValue())) {
                        dialog.dispose();
                    }
                };
        content.addPropertyChangeListener(cancellation);
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setVisible(true);
        content.removePropertyChangeListener(cancellation);
        return primaryLabel.equals(pane.getValue())
                ? DialogService.ReturnType.OK
                : DialogService.ReturnType.CANCEL;
    }
}
