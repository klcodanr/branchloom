package com.jagent.desktop.ui.components;

import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects web URLs in terminal output and delegates their navigation. */
final class TerminalLinkFilter implements HyperlinkFilter {
    private static final Pattern URL_PATTERN =
            Pattern.compile("(?i)\\bhttps?://[^\\s<>\\[\\]{}\"']+");
    private final Consumer<String> openUrl;

    protected TerminalLinkFilter(final Consumer<String> openUrl) {
        this.openUrl = openUrl;
    }

    @Override
    public LinkResult apply(final String line) {
        final Matcher matcher = URL_PATTERN.matcher(line);
        final var links = new ArrayList<LinkResultItem>();
        while (matcher.find()) {
            final String url = trimTrailingPunctuation(matcher.group());
            if (!url.isEmpty()) {
                links.add(
                        new LinkResultItem(
                                matcher.start(),
                                matcher.start() + url.length(),
                                new LinkInfo(() -> openUrl.accept(url))));
            }
        }
        return links.isEmpty() ? null : new LinkResult(links);
    }

    private static String trimTrailingPunctuation(final String value) {
        var end = value.length();
        while (end > 0 && ".,;:!?)]}".indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }
}
