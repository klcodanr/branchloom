package com.jagent.desktop.ui.components;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.jagent.desktop.api.Action;
import java.awt.Color;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.UIManager;

/** Shared Lucide icons used by the Swing UI. */
public final class UiIcons {
    private static final String FOLDER_OPEN = "folder-open";
    private static final String SETTINGS = "settings";
    private static final Map<String, String> ACTION_ICONS =
            Map.ofEntries(
                    Map.entry("new-project", "plus"),
                    Map.entry("new-session", "plus"),
                    Map.entry("open-terminal", "terminal"),
                    Map.entry("open-project", FOLDER_OPEN),
                    Map.entry("open-session", FOLDER_OPEN),
                    Map.entry("find", "search"),
                    Map.entry("settings", SETTINGS),
                    Map.entry("project-settings", SETTINGS),
                    Map.entry("problems", "alert-circle"),
                    Map.entry("resource-usage", "activity"),
                    Map.entry("about", "info"),
                    Map.entry("shortcuts", "keyboard"),
                    Map.entry("open-directory", FOLDER_OPEN),
                    Map.entry("copy-path", "copy"),
                    Map.entry("copy-branch", "copy"),
                    Map.entry("import-branch", "download"),
                    Map.entry("import-worktree", "download"),
                    Map.entry("remove-project", "trash-2"),
                    Map.entry("remove-session", "trash-2"));

    private UiIcons() {}

    public static Icon plus() {
        return icon("plus");
    }

    public static Icon terminal() {
        return icon("terminal");
    }

    public static Icon hatGlasses() {
        return icon("hat-glasses");
    }

    public static Icon settings() {
        return icon(SETTINGS);
    }

    public static Icon search() {
        return icon("search");
    }

    public static Icon ellipsis() {
        return icon("ellipsis");
    }

    public static Icon activity() {
        return icon("activity");
    }

    public static Icon alertCircle() {
        return icon("alert-circle");
    }

    public static Icon copy() {
        return icon("copy");
    }

    public static Icon download() {
        return icon("download");
    }

    public static Icon folderOpen() {
        return icon(FOLDER_OPEN);
    }

    public static Icon fileCode() {
        return icon("file-code");
    }

    public static Icon gitCompare() {
        return icon("git-compare");
    }

    public static Icon gitCompareArrows() {
        return icon("git-compare-arrows");
    }

    public static Icon funnel() {
        return icon("funnel");
    }

    public static Icon funnelX() {
        return icon("funnel-x");
    }

    public static Icon userRoundArrowLeft() {
        return icon("user-round-arrow-left");
    }

    public static Icon messageSquareDiff() {
        return icon("message-square-diff");
    }

    public static Icon refresh() {
        return icon("refresh-cw");
    }

    public static Icon chevronRight() {
        return icon("chevron-right");
    }

    public static Icon forAction(final Action action) {
        final String iconName =
                ACTION_ICONS.containsKey(action.id())
                        ? ACTION_ICONS.get(action.id())
                        : action.id().startsWith("new-terminal-") ? "terminal" : null;
        return iconName == null ? null : icon(iconName);
    }

    private static Icon icon(final String name) {
        final Color foreground = UIManager.getColor("Label.foreground");
        return new FlatSVGIcon("icons/" + name + ".svg", 16, 16)
                .setColorFilter(new FlatSVGIcon.ColorFilter(ignored -> foreground));
    }
}
