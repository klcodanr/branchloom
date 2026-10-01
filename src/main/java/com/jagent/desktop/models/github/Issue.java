package com.jagent.desktop.models.github;

import java.net.URL;

public record Issue(int number, String title, String body, URL url) {}
