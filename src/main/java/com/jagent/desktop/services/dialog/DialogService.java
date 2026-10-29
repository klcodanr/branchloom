package com.jagent.desktop.services.dialog;

import java.nio.file.Path;
import javax.swing.JComponent;

/** Application boundary for all user-facing modal interactions. */
public interface DialogService {

    enum MessageType {
        ERROR,
        INFORMATION,
        WARNING,
        QUESTION,
        PLAIN
    }

    enum OptionType {
        OK_CANCEL,
        YES_NO
    }

    enum ReturnType {
        OK,
        CANCEL,
        NO,
        CLOSED
    }

    void message(String message, String title, MessageType messageType);

    void errorMessage(String message, String title);

    ReturnType confirm(
            Object content, String title, OptionType optionType, MessageType messageType);

    Object input(Object message, String title, MessageType messageType);

    ReturnType option(
            Object message,
            String title,
            MessageType messageType,
            Object[] options,
            Object initialValue);

    Path chooseDirectory(String title, Path initialDirectory, String approveButtonText);

    ReturnType show(String title, JComponent content, String primaryLabel);
}
