package com.jagent.desktop.ui.components;

import java.awt.Insets;

/**
 * Shared dimensions for the desktop UI.
 *
 * <p>Spacing follows a 4px base unit. Use the semantic values below for layout hierarchy: page
 * margins contain a view, section padding contains a group, component gaps separate siblings, and
 * content padding separates a control's content from its edge.
 */
public final class UiConstants {
    public static final String DISABLED_FOREGROUND = "Label.disabledForeground";
    public static final int SPACING_XS = 4;
    public static final int SPACING_SM = 8;
    public static final int SPACING_MD = 12;
    public static final int SPACING_LG = 16;
    public static final int SPACING_XL = 20;
    public static final int SPACING_2XL = 24;

    public static final int PAGE_MARGIN = SPACING_LG;
    public static final int SECTION_PADDING = SPACING_LG;
    public static final int COMPONENT_GAP = SPACING_MD;
    public static final int CONTENT_PADDING = SPACING_SM;
    public static final int TAB_INSET = CONTENT_PADDING;
    public static final int CARD_PADDING = SPACING_SM;
    public static final int TERMINAL_PADDING = SPACING_SM;
    public static final Insets ZERO_INSETS = new Insets(0, 0, 0, 0);
    public static final int PR_CARD_WIDTH = 200;
    public static final int PR_CARD_HEIGHT = 98;
    public static final int PROJECT_CARD_WIDTH = 250;
    public static final int PROJECT_CARD_HEIGHT = 112;
    public static final int METRIC_WIDTH = 150;
    public static final int METRIC_HEIGHT = 58;

    private UiConstants() {}
}
