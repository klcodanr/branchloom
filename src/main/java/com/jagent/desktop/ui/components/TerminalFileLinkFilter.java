package com.jagent.desktop.ui.components;

import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects existing files and compiler-style line and column suffixes. */
final class TerminalFileLinkFilter implements HyperlinkFilter {
    private static final Pattern TOKEN = Pattern.compile("(?<!\\S)[^\\s<>\\[\\]{}\"']+");
    private static final Pattern LOCATION = Pattern.compile("^(.+?)(?::(\\d+))?(?::(\\d+))?$");
    private final Path directory;
    private final Consumer<TerminalFileLink> openFile;
    private final BooleanSupplier activationAllowed;

    protected TerminalFileLinkFilter(
            final Path directory, final Consumer<TerminalFileLink> openFile) {
        this(directory, openFile, () -> true);
    }

    protected TerminalFileLinkFilter(
            final Path directory,
            final Consumer<TerminalFileLink> openFile,
            final BooleanSupplier activationAllowed) {
        this.directory = directory.toAbsolutePath().normalize();
        this.openFile = openFile;
        this.activationAllowed = activationAllowed;
    }

    @Override
    public LinkResult apply(final String line) {
        final Matcher matcher = TOKEN.matcher(line);
        final var links = new ArrayList<LinkResultItem>();
        while (matcher.find()) {
            final String candidate = trimPunctuation(matcher.group());
            final TerminalFileLink link = parse(candidate);
            if (link != null) {
                final int start = matcher.start();
                links.add(
                        new LinkResultItem(
                                start,
                                start + candidate.length(),
                                new TerminalLinkInfo(
                                        candidate,
                                        activationAllowed,
                                        () -> openFile.accept(link))));
            }
        }
        return links.isEmpty() ? null : new LinkResult(links);
    }

    private TerminalFileLink parse(final String candidate) {
        if (candidate.contains("://")) {
            return null;
        }
        final Matcher location = LOCATION.matcher(candidate);
        if (!location.matches()) {
            return null;
        }
        if (!looksLikePath(location.group(1))) {
            return null;
        }
        final int line = number(location.group(2));
        final int column = number(location.group(3));
        final Path path = resolve(location.group(1));
        return Files.isRegularFile(path) ? new TerminalFileLink(path, line, column) : null;
    }

    private static boolean looksLikePath(final String value) {
        return value.contains("/")
                || value.contains("\\")
                || value.startsWith(".")
                || value.matches(".*\\.[^./\\\\]+$");
    }

    private Path resolve(final String value) {
        final String pathValue =
                value.startsWith("~/")
                        ? Path.of(System.getProperty("user.home"), value.substring(2)).toString()
                        : value;
        return Path.of(pathValue).isAbsolute()
                ? Path.of(pathValue).normalize()
                : directory.resolve(pathValue).normalize();
    }

    private static int number(final String value) {
        return value == null ? 0 : Integer.parseInt(value);
    }

    private static String trimPunctuation(final String value) {
        var end = value.length();
        while (end > 0 && ".,;:!?)]}".indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }
}
