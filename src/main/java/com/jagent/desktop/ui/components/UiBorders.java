package com.jagent.desktop.ui.components;

import com.formdev.flatlaf.ui.FlatLineBorder;
import java.awt.Color;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/** Borders used by the application's page and panel layouts. */
public final class UiBorders {
    private UiBorders() {}

    public static Border page() {
        return new EmptyBorder(
                UiConstants.PAGE_MARGIN,
                UiConstants.PAGE_MARGIN,
                UiConstants.PAGE_MARGIN,
                UiConstants.PAGE_MARGIN);
    }

    public static Border section() {
        return new EmptyBorder(
                UiConstants.SECTION_PADDING,
                UiConstants.SECTION_PADDING,
                UiConstants.SECTION_PADDING,
                UiConstants.SECTION_PADDING);
    }

    public static Border contentArea() {
        final Color borderColor = Theme.Colors.border();
        final Insets padding =
                new Insets(
                        UiConstants.SPACING_SM,
                        UiConstants.SPACING_SM,
                        UiConstants.SPACING_SM,
                        UiConstants.SPACING_SM);
        return BorderFactory.createCompoundBorder(
                new EmptyBorder(
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS),
                new FlatLineBorder(padding, borderColor, 1f, 8));
    }

    public static Border card() {
        return new EmptyBorder(
                UiConstants.CARD_PADDING,
                UiConstants.CARD_PADDING,
                UiConstants.CARD_PADDING,
                UiConstants.CARD_PADDING);
    }
}
