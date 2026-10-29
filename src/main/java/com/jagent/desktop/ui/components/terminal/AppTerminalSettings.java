package com.jagent.desktop.ui.components.terminal;

import com.jagent.desktop.ui.components.Theme;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.Font;

public final class AppTerminalSettings extends DefaultSettingsProvider {

    private TerminalColor terminalColor(final java.awt.Color color) {
        return TerminalColor.rgb(color.getRed(), color.getGreen(), color.getBlue());
    }

    @Override
    public Font getTerminalFont() {
        return Theme.terminalFont(Theme.FontSize.SM);
    }

    @Override
    public float getTerminalFontSize() {
        return Theme.FontSize.SM.points();
    }

    @Override
    public TerminalColor getDefaultForeground() {
        return terminalColor(Theme.Colors.foreground());
    }

    @Override
    public TerminalColor getDefaultBackground() {
        return terminalColor(Theme.Colors.background());
    }

    @Override
    public TextStyle getDefaultStyle() {
        return new TextStyle(getDefaultForeground(), getDefaultBackground());
    }

    @Override
    public TextStyle getSelectionColor() {
        final java.awt.Color selectionBackground = Theme.Colors.focus();
        return new TextStyle(
                terminalColor(contrastingForeground(selectionBackground)),
                terminalColor(selectionBackground));
    }

    @Override
    public boolean useInverseSelectionColor() {
        return false;
    }

    @Override
    public TextStyle getFoundPatternColor() {
        final java.awt.Color foundPatternBackground = Theme.Colors.warning();
        return new TextStyle(
                terminalColor(contrastingForeground(foundPatternBackground)),
                terminalColor(foundPatternBackground));
    }

    @Override
    public TextStyle getHyperlinkColor() {
        return new TextStyle(
                terminalColor(Theme.Colors.focus()), terminalColor(Theme.Colors.background()));
    }

    @Override
    public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
        return HyperlinkStyle.HighlightMode.ALWAYS;
    }

    @Override
    public int caretBlinkingMs() {
        return 650;
    }

    private static java.awt.Color contrastingForeground(final java.awt.Color background) {
        final java.awt.Color panelBackground = Theme.Colors.background();
        final java.awt.Color labelForeground = Theme.Colors.foreground();
        final double labelContrast = contrastRatio(background, labelForeground);
        final double panelContrast = contrastRatio(background, panelBackground);
        return labelContrast >= panelContrast ? labelForeground : panelBackground;
    }

    private static double contrastRatio(
            final java.awt.Color background, final java.awt.Color foreground) {
        final double backgroundLuminance = relativeLuminance(background);
        final double foregroundLuminance = relativeLuminance(foreground);
        final double lighter = Math.max(backgroundLuminance, foregroundLuminance);
        final double darker = Math.min(backgroundLuminance, foregroundLuminance);
        return (lighter + 0.05d) / (darker + 0.05d);
    }

    private static double relativeLuminance(final java.awt.Color color) {
        final double red = linearize(color.getRed() / 255d);
        final double green = linearize(color.getGreen() / 255d);
        final double blue = linearize(color.getBlue() / 255d);
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private static double linearize(final double component) {
        if (component <= 0.039_28d) {
            return component / 12.92d;
        }
        return Math.pow((component + 0.055d) / 1.055d, 2.4d);
    }
}
