package com.jagent.desktop.ui.components;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.jagent.desktop.services.PlatformCommands;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/** FlatLaf theme selection and typography helpers. */
public final class Theme {
    private Theme() {}

    public enum FlatLafTheme {
        SYSTEM("System", null),
        LIGHT("Light", FlatLightLaf.class),
        DARK("Dark", com.formdev.flatlaf.FlatDarkLaf.class),
        MAC_DARK("Mac Dark", FlatMacDarkLaf.class),
        INTELLIJ("IntelliJ", FlatIntelliJLaf.class);

        private final String label;
        private final Class<? extends javax.swing.LookAndFeel> lookAndFeel;

        FlatLafTheme(
                final String label, final Class<? extends javax.swing.LookAndFeel> lookAndFeel) {
            this.label = label;
            this.lookAndFeel = lookAndFeel;
        }

        @Override
        public String toString() {
            return label;
        }

        private javax.swing.LookAndFeel createLookAndFeel() {
            try {
                if (lookAndFeel == null) {
                    return resolve().createLookAndFeel();
                }
                return lookAndFeel.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "Could not create FlatLaf theme " + label, exception);
            }
        }

        public static FlatLafTheme from(final String value) {
            for (final FlatLafTheme theme : values()) {
                if (theme.label.equalsIgnoreCase(value)) {
                    return theme;
                }
            }
            return SYSTEM;
        }

        public FlatLafTheme resolve() {
            if (this != SYSTEM) {
                return this;
            }
            return systemIsDark() ? DARK : LIGHT;
        }

        private static boolean systemIsDark() {
            if (PlatformCommands.isMac()) {
                return macIsDark();
            }
            if (PlatformCommands.isWindows()) {
                return windowsIsDark();
            }

            final String gtkTheme = System.getenv("GTK_THEME");
            if (containsDark(gtkTheme)) {
                return true;
            }
            return containsDark(
                            commandOutput(
                                    "gsettings",
                                    "get",
                                    "org.gnome.desktop.interface",
                                    "color-scheme"))
                    || containsDark(
                            commandOutput(
                                    "gsettings",
                                    "get",
                                    "org.gnome.desktop.interface",
                                    "gtk-theme"));
        }

        private static boolean macIsDark() {
            String appearance = System.getProperty("apple.awt.application.appearance");
            if (appearance == null) {
                try {
                    final Object desktopAppearance =
                            Toolkit.getDefaultToolkit()
                                    .getDesktopProperty("apple.awt.application.appearance");
                    if (desktopAppearance != null) {
                        appearance = desktopAppearance.toString();
                    }
                } catch (RuntimeException ignored) {
                    // Fall back to the system preference below.
                }
            }
            return containsDark(appearance)
                    || containsDark(commandOutput("defaults", "read", "-g", "AppleInterfaceStyle"));
        }

        private static boolean windowsIsDark() {
            final String value =
                    commandOutput(
                            "reg",
                            "query",
                            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                            "/v",
                            "AppsUseLightTheme");
            return value != null && value.matches("(?s).*AppsUseLightTheme\\s+REG_DWORD\\s+0x0.*");
        }

        private static boolean containsDark(final String value) {
            return value != null && value.toLowerCase(Locale.ROOT).contains("dark");
        }

        private static String commandOutput(final String... command) {
            try {
                final Process process =
                        new ProcessBuilder(command).redirectErrorStream(true).start();
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return null;
                }
                if (process.exitValue() != 0) {
                    return null;
                }
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                return null;
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    public enum FontSize {
        XS(11),
        SM(12),
        MD(13),
        LG(14),
        XL(20),
        XXL(25);

        private final int points;

        FontSize(final int points) {
            this.points = points;
        }

        public int points() {
            return points;
        }
    }

    public static Font font(final FontSize size) {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = UIManager.getFont("Label.font");
        }
        if (base == null) {
            throw new IllegalStateException("Active look and feel does not define a font");
        }
        return base.deriveFont((float) size.points);
    }

    public static Font boldFont(final FontSize size) {
        return font(size).deriveFont(Font.BOLD);
    }

    public static Font terminalFont(final FontSize size) {
        Font base = UIManager.getFont("TextArea.font");
        if (base == null) {
            base = UIManager.getFont("Label.font");
        }
        if (base == null) {
            throw new IllegalStateException("Active look and feel does not define a font");
        }
        return new Font(Font.MONOSPACED, base.getStyle(), size.points);
    }

    public static Color successColor() {
        return color("Actions.Green", "Component.focusColor");
    }

    public static Color warningColor() {
        return color("Actions.Yellow", "Component.focusColor");
    }

    public static Color dangerColor() {
        return color("Actions.Red", "Component.focusColor");
    }

    public static Color mutedColor() {
        return color(UiConstants.DISABLED_FOREGROUND, "Label.foreground");
    }

    private static Color color(final String key, final String fallbackKey) {
        final Color color = UIManager.getColor(key);
        return color == null ? UIManager.getColor(fallbackKey) : color;
    }

    public static Border sectionBorder(
            final int top, final int left, final int bottom, final int right) {
        Color borderColor = UIManager.getColor("Component.borderColor");
        if (borderColor == null) {
            borderColor = UIManager.getColor("Separator.foreground");
        }
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(top, left, bottom, right));
    }

    public static void apply(final FlatLafTheme theme) {
        if (!FlatLaf.setup(theme.createLookAndFeel())) {
            throw new IllegalStateException("Could not apply FlatLaf theme " + theme);
        }
        applySwingDefaults();
        FlatLaf.updateUI();
    }

    public static void applySwingDefaults() {
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 8);
        UIManager.put("Button.minimumHeight", 30);
        UIManager.put("Button.margin", new java.awt.Insets(5, 12, 5, 12));
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("TextField.margin", new java.awt.Insets(6, 10, 6, 10));
        UIManager.put("TextArea.margin", new java.awt.Insets(8, 10, 8, 10));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Tree.selectionBackground", UIManager.getColor("Panel.background"));
        UIManager.put("OptionPane.border", BorderFactory.createEmptyBorder(16, 16, 12, 16));
    }
}
