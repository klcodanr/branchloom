package com.jagent.desktop.async;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public interface BatchListener<P> extends AsyncStateListener<List<P>> {
    void onMessage(String message);

    void onProgress(int progress, @Nullable List<P> progressData);

    void onStart(int total);
}
