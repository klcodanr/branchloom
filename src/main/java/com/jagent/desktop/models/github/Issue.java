package com.jagent.desktop.models.github;

import java.net.URL;
import java.util.Date;

public record Issue(
        int number, String title, String body, URL url, Date createdAt, Date updatedAt) {
    public Issue(final int number, final String title, final String body, final URL url) {
        this(number, title, body, url, new Date(), new Date());
    }
}
