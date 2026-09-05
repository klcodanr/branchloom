package com.jagent.desktop.ui.utils;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SessionNames {
    private SessionNames() {}

    public static Set<String> existing(final AppState state, final Project project) {
        final Set<String> names = new HashSet<>();
        project.sessionIds().stream()
                .map(state.sessions()::get)
                .filter(session -> session != null)
                .map(Session::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(names::add);
        return names;
    }

    public static String unique(final String base, final Set<String> names) {
        String name = base;
        int suffix = 2;
        boolean exists;
        do {
            exists = false;
            for (final String existing : names) {
                if (existing.equalsIgnoreCase(name)) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                name = base + "-" + suffix++;
            }
        } while (exists);
        return name;
    }
}
