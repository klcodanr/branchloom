package com.jagent.desktop.async;

public interface AsyncStateListener<V> {
    /** Called on the Swing event dispatch thread after a fetch completes successfully. */
    void onComplete(V data);

    /** Called on the Swing event dispatch thread after a fetch fails. */
    void onFailure(Throwable error);
}
