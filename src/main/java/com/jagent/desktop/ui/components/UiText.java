package com.jagent.desktop.ui.components;

import java.awt.Color;
import java.util.Locale;

/** Formatting helpers for values displayed by Swing components. */
public final class UiText {
    private UiText() {}

    public static String escapeHtml(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String colorHex(final Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String titleCase(final String value) {
        final String[] words = value.toLowerCase(Locale.ROOT).split("_");
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public static Color checksColor(final String checksStatus) {
        return switch (checksStatus) {
            case "PASSING" -> Theme.successColor();
            case "FAILING" -> Theme.dangerColor();
            case "PENDING" -> Theme.warningColor();
            default -> Theme.mutedColor();
        };
    }
}
