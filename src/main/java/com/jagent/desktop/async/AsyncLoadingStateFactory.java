package com.jagent.desktop.async;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AsyncLoadingStateFactory {
    private final Map<Object, AsyncLoadingState<?, ?, ?, ?>> asyncLoadingStates =
            new ConcurrentHashMap<>();

    @Inject
    public AsyncLoadingStateFactory(final AppState appState, final BackgroundJobs backgroundJobs) {

        this.asyncLoadingStates.put(ViewId.BOARD, new BoardInstanceState(appState, backgroundJobs));
        this.asyncLoadingStates.put(
                ViewId.SESSION, new SessionInstanceState(appState, backgroundJobs));
    }

    @SuppressWarnings("unchecked")
    public <V extends AsyncLoadingState<?, ?, ?, ?>> V getAsyncLoadingState(final Object key) {
        return (V) asyncLoadingStates.get(key);
    }

    @SuppressWarnings("unchecked")
    public <V extends AsyncLoadingState<?, ?, ?, ?>> V getOrDefaultAsyncLoadingState(
            final Object key, final V defaultState) {
        if (!asyncLoadingStates.containsKey(key)) {
            asyncLoadingStates.put(key, defaultState);
        }
        return (V) asyncLoadingStates.get(key);
    }
}
