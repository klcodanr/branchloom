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
        while (names.contains(name.toLowerCase(Locale.ROOT))) {
            name = base + "-" + suffix++;
        }
        return name;
    }
}
