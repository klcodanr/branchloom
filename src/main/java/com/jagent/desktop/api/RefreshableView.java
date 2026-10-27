package com.jagent.desktop.api;

import java.util.concurrent.CompletableFuture;

/** A view that can be refreshed from the application chrome. */
public interface RefreshableView extends View {
    CompletableFuture<?> refresh();
}
