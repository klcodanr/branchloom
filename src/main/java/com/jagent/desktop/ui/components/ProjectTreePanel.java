package com.jagent.desktop.ui.components;

import com.google.inject.Inject;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.tree.LeftNavTree;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public final class ProjectTreePanel extends JPanel {

    private final LeftNavTree tree;
    private final SearchInput search;

    @Inject
    public ProjectTreePanel(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final SessionActions sessionActions,
            final ProjectActions projectActions) {
        super();
        setBorder(UiBorders.contentArea());
        setPreferredSize(new Dimension(315, 0));
        setLayout(new BorderLayout(0, UiConstants.COMPONENT_GAP));

        add(new NewProjectsHeader(viewController, appState, dialogService), BorderLayout.NORTH);
        search =
                new SearchInput(
                        new SearchInput.Text(
                                "project-search",
                                "Search projects and sessions",
                                "Search projects and sessions"));
        search.setVisible(false);

        tree =
                new LeftNavTree(
                        (searchQuery) -> {
                            search.setText(searchQuery);
                            search.setVisible(true);
                        },
                        viewController,
                        appState,
                        dialogService,
                        sessionActions,
                        projectActions);

        search.onChange(tree::setSearchQuery);
        search.onCancel(
                () -> {
                    tree.setSearchQuery("");
                    search.setVisible(false);
                });
        final JPanel treeContent = new JPanel(new BorderLayout(0, UiConstants.CONTENT_PADDING));
        treeContent.add(search, BorderLayout.NORTH);
        final JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(null);
        treeScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        treeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        treeContent.add(treeScroll, BorderLayout.CENTER);
        add(treeContent, BorderLayout.CENTER);
    }
}
