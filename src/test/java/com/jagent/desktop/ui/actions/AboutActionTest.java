package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.dialog.DialogService;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AboutActionTest {
    @Test
    void showsRuntimeInformation() {
        final AtomicReference<String> message = new AtomicReference<>();
        final DialogService dialogs =
                (DialogService)
                        Proxy.newProxyInstance(
                                Thread.currentThread().getContextClassLoader(),
                                new Class<?>[] {DialogService.class},
                                (proxy, method, args) -> {
                                    if ("message".equals(method.getName())) {
                                        message.set((String) args[0]);
                                    }
                                    return null;
                                });

        new AboutAction(dialogs).execute();

        assertTrue(
                message.get().contains("Java: " + System.getProperty("java.version")),
                "About dialog should show the Java version");
        assertTrue(
                message.get().contains("OS: " + System.getProperty("os.name")),
                "About dialog should show the operating system");
    }
}
