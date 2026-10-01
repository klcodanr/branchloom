import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.UIManager;

final class ListUiColors {
    private ListUiColors() {}

    public static void main(final String[] args) throws IOException {
        final Map<String, Map<String, Color>> colorsByTheme = new LinkedHashMap<>();
        for (final Theme theme : Theme.values()) {
            theme.install();
            colorsByTheme.put(theme.label, colors());
        }

        final List<String> commonKeys = new ArrayList<>(colorsByTheme.values().iterator().next().keySet());
        colorsByTheme.values().stream().skip(1).forEach(colors -> commonKeys.removeIf(key -> !colors.containsKey(key)));
        commonKeys.sort(Comparator.naturalOrder());

        final Path output = Path.of(args.length == 0 ? "docs/SUPPORTED_COLORS.md" : args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, markdown(colorsByTheme, commonKeys));
        System.out.println("Wrote " + output);
    }

    private static String markdown(
            final Map<String, Map<String, Color>> colorsByTheme, final List<String> commonKeys) {
        final StringBuilder markdown = new StringBuilder("# Supported UI Colors\n\n");
        markdown.append(
                "Color keys available in every supported FlatLaf theme. Values are resolved at runtime.\n\n| Key |");
        colorsByTheme.keySet().forEach(theme -> markdown.append(" ").append(theme).append(" |"));
        markdown.append("\n| --- |");
        colorsByTheme.keySet().forEach(theme -> markdown.append(" --- |"));
        markdown.append("\n");
        for (final String key : commonKeys) {
            markdown.append("| `").append(key).append("` |");
            for (final Map<String, Color> colors : colorsByTheme.values()) {
                final Color color = colors.get(key);
                markdown.append(
                        String.format(
                                " <span style=\"display:inline-block;width:1.2em;height:1.2em;"
                                        + "background-color:rgb(%d,%d,%d);vertical-align:middle\"></span> "
                                        + "`rgb(%d, %d, %d)` |",
                                color.getRed(),
                                color.getGreen(),
                                color.getBlue(),
                                color.getRed(),
                                color.getGreen(),
                                color.getBlue()));
            }
            markdown.append("\n");
        }
        return markdown.toString();
    }

    private static Map<String, Color> colors() {
        final Map<String, Color> colors = new LinkedHashMap<>();
        UIManager.getDefaults().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Color)
                .forEach(entry -> colors.put(entry.getKey().toString(), (Color) entry.getValue()));
        return colors;
    }

    private enum Theme {
        LIGHT("Light") {
            @Override
            void install() {
                FlatLightLaf.setup();
            }
        },
        DARK("Dark") {
            @Override
            void install() {
                FlatDarkLaf.setup();
            }
        },
        MAC_DARK("Mac Dark") {
            @Override
            void install() {
                FlatMacDarkLaf.setup();
            }
        },
        INTELLIJ("IntelliJ") {
            @Override
            void install() {
                FlatIntelliJLaf.setup();
            }
        };

        private final String label;

        Theme(final String label) {
            this.label = label;
        }

        abstract void install();
    }
}
