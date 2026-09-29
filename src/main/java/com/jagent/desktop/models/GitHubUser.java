package com.jagent.desktop.models;

import java.net.URL;
import org.kohsuke.github.GHUser;

public record GitHubUser(String login, URL url) {

    public static GitHubUser from(final GHUser user) {
        return new GitHubUser(user.getLogin(), user.getHtmlUrl());
    }
}
