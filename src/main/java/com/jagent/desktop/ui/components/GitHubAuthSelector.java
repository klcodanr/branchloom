package com.jagent.desktop.ui.components;

import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.GitHub.Auth;
import java.awt.Component;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

public final class GitHubAuthSelector {
    private GitHubAuthSelector() {}

    public static JComboBox<Auth> render() {
        final List<GitHub.Auth> configuredAuths = GitHub.configuredAuths();
        final JComboBox<GitHub.Auth> githubAuth =
                new JComboBox<>(
                        java.util.stream.Stream.concat(
                                        java.util.stream.Stream.of((GitHub.Auth) null),
                                        configuredAuths.stream())
                                .toArray(GitHub.Auth[]::new));
        githubAuth.setRenderer(
                new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(
                            final JList<?> list,
                            final Object value,
                            final int index,
                            final boolean selected,
                            final boolean focused) {
                        return super.getListCellRendererComponent(
                                list,
                                value == null ? "Default (active account)" : value,
                                index,
                                selected,
                                focused);
                    }
                });
        return githubAuth;
    }
}
