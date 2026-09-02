package com.jagent.desktop.ui.components;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/** Maps workspace file names to RSyntaxTextArea syntax styles. */
public final class FileSyntax {
    private static final Map<String, String> FILE_NAMES =
            Map.ofEntries(
                    Map.entry("dockerfile", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE),
                    Map.entry(".htaccess", SyntaxConstants.SYNTAX_STYLE_HTACCESS),
                    Map.entry(".bashrc", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
                    Map.entry(".zshrc", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
                    Map.entry("makefile", SyntaxConstants.SYNTAX_STYLE_MAKEFILE),
                    Map.entry("cmakelists.txt", SyntaxConstants.SYNTAX_STYLE_MAKEFILE),
                    Map.entry("hosts", SyntaxConstants.SYNTAX_STYLE_HOSTS),
                    Map.entry("jenkinsfile", SyntaxConstants.SYNTAX_STYLE_GROOVY),
                    Map.entry("gemfile", SyntaxConstants.SYNTAX_STYLE_RUBY));
    private static final Map<String, String> EXTENSIONS =
            Map.ofEntries(
                    Map.entry(".as", SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT),
                    Map.entry(".a65", SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_6502),
                    Map.entry(".asm", SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86),
                    Map.entry(".bb", SyntaxConstants.SYNTAX_STYLE_BBCODE),
                    Map.entry(".bat", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH),
                    Map.entry(".csv", SyntaxConstants.SYNTAX_STYLE_CSV),
                    Map.entry(".c", SyntaxConstants.SYNTAX_STYLE_C),
                    Map.entry(".cc", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
                    Map.entry(".cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
                    Map.entry(".cs", SyntaxConstants.SYNTAX_STYLE_CSHARP),
                    Map.entry(".css", SyntaxConstants.SYNTAX_STYLE_CSS),
                    Map.entry(".clj", SyntaxConstants.SYNTAX_STYLE_CLOJURE),
                    Map.entry(".cljs", SyntaxConstants.SYNTAX_STYLE_CLOJURE),
                    Map.entry(".d", SyntaxConstants.SYNTAX_STYLE_D),
                    Map.entry(".dart", SyntaxConstants.SYNTAX_STYLE_DART),
                    Map.entry(".dockerfile", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE),
                    Map.entry(".pas", SyntaxConstants.SYNTAX_STYLE_DELPHI),
                    Map.entry(".dtd", SyntaxConstants.SYNTAX_STYLE_DTD),
                    Map.entry(".f", SyntaxConstants.SYNTAX_STYLE_FORTRAN),
                    Map.entry(".f90", SyntaxConstants.SYNTAX_STYLE_FORTRAN),
                    Map.entry(".go", SyntaxConstants.SYNTAX_STYLE_GO),
                    Map.entry(".groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY),
                    Map.entry(".hbs", SyntaxConstants.SYNTAX_STYLE_HANDLEBARS),
                    Map.entry(".h", SyntaxConstants.SYNTAX_STYLE_C),
                    Map.entry(".hh", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
                    Map.entry(".hpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
                    Map.entry(".htm", SyntaxConstants.SYNTAX_STYLE_HTML),
                    Map.entry(".html", SyntaxConstants.SYNTAX_STYLE_HTML),
                    Map.entry(".ini", SyntaxConstants.SYNTAX_STYLE_INI),
                    Map.entry(".java", SyntaxConstants.SYNTAX_STYLE_JAVA),
                    Map.entry(".js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
                    Map.entry(".jsx", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
                    Map.entry(".json", SyntaxConstants.SYNTAX_STYLE_JSON),
                    Map.entry(".jsonc", SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS),
                    Map.entry(".jsp", SyntaxConstants.SYNTAX_STYLE_JSP),
                    Map.entry(".kt", SyntaxConstants.SYNTAX_STYLE_KOTLIN),
                    Map.entry(".kts", SyntaxConstants.SYNTAX_STYLE_KOTLIN),
                    Map.entry(".tex", SyntaxConstants.SYNTAX_STYLE_LATEX),
                    Map.entry(".less", SyntaxConstants.SYNTAX_STYLE_LESS),
                    Map.entry(".lua", SyntaxConstants.SYNTAX_STYLE_LUA),
                    Map.entry(".lisp", SyntaxConstants.SYNTAX_STYLE_LISP),
                    Map.entry(".cl", SyntaxConstants.SYNTAX_STYLE_LISP),
                    Map.entry(".md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN),
                    Map.entry(".markdown", SyntaxConstants.SYNTAX_STYLE_MARKDOWN),
                    Map.entry(".mxml", SyntaxConstants.SYNTAX_STYLE_MXML),
                    Map.entry(".php", SyntaxConstants.SYNTAX_STYLE_PHP),
                    Map.entry(".pl", SyntaxConstants.SYNTAX_STYLE_PERL),
                    Map.entry(".nsi", SyntaxConstants.SYNTAX_STYLE_NSIS),
                    Map.entry(".proto", SyntaxConstants.SYNTAX_STYLE_PROTO),
                    Map.entry(".properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE),
                    Map.entry(".py", SyntaxConstants.SYNTAX_STYLE_PYTHON),
                    Map.entry(".rb", SyntaxConstants.SYNTAX_STYLE_RUBY),
                    Map.entry(".rs", SyntaxConstants.SYNTAX_STYLE_RUST),
                    Map.entry(".scala", SyntaxConstants.SYNTAX_STYLE_SCALA),
                    Map.entry(".scss", SyntaxConstants.SYNTAX_STYLE_CSS),
                    Map.entry(".sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
                    Map.entry(".bash", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
                    Map.entry(".zsh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
                    Map.entry(".fish", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
                    Map.entry(".sql", SyntaxConstants.SYNTAX_STYLE_SQL),
                    Map.entry(".swift", SyntaxConstants.SYNTAX_STYLE_C),
                    Map.entry(".tcl", SyntaxConstants.SYNTAX_STYLE_TCL),
                    Map.entry(".ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
                    Map.entry(".tsx", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
                    Map.entry(".vb", SyntaxConstants.SYNTAX_STYLE_VISUAL_BASIC),
                    Map.entry(".xml", SyntaxConstants.SYNTAX_STYLE_XML),
                    Map.entry(".yaml", SyntaxConstants.SYNTAX_STYLE_YAML),
                    Map.entry(".yml", SyntaxConstants.SYNTAX_STYLE_YAML));

    private FileSyntax() {}

    public static String styleFor(final Path path) {
        final Path name = path.getFileName();
        if (name == null) {
            return SyntaxConstants.SYNTAX_STYLE_NONE;
        }
        final String value = name.toString().toLowerCase(Locale.ROOT);
        final String fileStyle = FILE_NAMES.get(value);
        if (fileStyle != null) {
            return fileStyle;
        }
        return EXTENSIONS.entrySet().stream()
                .filter(entry -> value.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(SyntaxConstants.SYNTAX_STYLE_NONE);
    }
}
