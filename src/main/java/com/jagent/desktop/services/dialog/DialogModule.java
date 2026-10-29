package com.jagent.desktop.services.dialog;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/** Guice bindings for the application dialog boundary. */
public final class DialogModule extends AbstractModule {
    private final Window owner;

    @Override
    protected void configure() {}

    public DialogModule(final Window owner) {
        super();
        this.owner = owner;
    }

    @Provides
    @Singleton
    public DialogService dialogService() {
        return GraphicsEnvironment.isHeadless()
                ? new HeadlessDialogService()
                : new SwingDialogService(owner);
    }
}
