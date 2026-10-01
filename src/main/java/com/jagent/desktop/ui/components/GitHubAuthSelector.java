package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.github.CliCredential;
import com.jagent.desktop.models.github.Credential;
import java.awt.Component;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

public final class GitHubAuthSelector {
    private GitHubAuthSelector() {}

    public static JComboBox<Credential> render() {
        return renderConfigured(
                List.of(new CliCredential("github.com", "Default (active account)", "")));
    }

    public static JComboBox<Credential> renderConfigured(final List<Credential> configuredAuths) {
        final JComboBox<Credential> githubAuth =
                new JComboBox<>(
                        Stream.concat(Stream.of((Credential) null), configuredAuths.stream())
                                .toArray(Credential[]::new));
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
                                value == null
                                        ? "Default (active account)"
                                        : ((Credential) value).name(),
                                index,
                                selected,
                                focused);
                    }
                });
        return githubAuth;
    }
}
