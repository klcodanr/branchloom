package com.jagent.desktop.ui.layout;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import com.jagent.desktop.ui.components.UiFactory;
import com.jagent.desktop.ui.utils.PathUtils;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public final class WorkspaceTree extends JTree {
    public static final String LOADING = "Loading...";
    private final transient Actions actions;
    private final transient ActionContext context;

    public WorkspaceTree(
            final DefaultMutableTreeNode root,
            final Path workspace,
            final ActionContext context,
            final Actions actions) {
        super(new DefaultTreeModel(root));
        this.actions = actions;
        this.context = context;
        setRootVisible(true);
        setShowsRootHandles(true);
        setCellRenderer(
                new WorkspaceTreeCellRenderer(
                        workspace.toAbsolutePath().normalize(), actions.statusForPath()));
        getAccessibleContext().setAccessibleName("Workspace files");
        getInputMap(WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-selected-file");
        getActionMap()
                .put(
                        "open-selected-file",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(final java.awt.event.ActionEvent event) {
                                openSelectedPath();
                            }
                        });
        getInputMap(WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "show-context-menu");
        getInputMap(WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK),
                        "show-context-menu");
        getActionMap()
                .put(
                        "show-context-menu",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(final java.awt.event.ActionEvent event) {
                                showKeyboardMenu();
                            }
                        });
        installMouseListener();
    }

    private void openSelectedPath() {
        final TreePath selected = getSelectionPath();
        if (selected == null) {
            return;
        }
        final Object value =
                ((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject();
        if (value instanceof Path path) {
            actions.onOpenSelectedPath().accept(path);
        }
    }

    private void showKeyboardMenu() {
        final TreePath selected = getSelectionPath();
        if (selected == null) {
            return;
        }
        final java.awt.Rectangle bounds = getPathBounds(selected);
        if (bounds != null) {
            showMenuAt(selected, bounds.x, bounds.y + bounds.height);
        }
    }

    private void showMenuAt(final TreePath treePath, final int x, final int y) {
        setSelectionPath(treePath);
        final Object value =
                ((DefaultMutableTreeNode) treePath.getLastPathComponent()).getUserObject();
        if (!(value instanceof Path path)) {
            return;
        }
        final JPopupMenu menu = new JPopupMenu();
        if (!PathUtils.directory(path)) {
            final JMenuItem open = new JMenuItem("Open in editor");
            open.addActionListener(ignored -> actions.onOpenInEditor().accept(path));
            menu.add(open);
        }
        final JMenuItem reveal = new JMenuItem("Reveal in file manager");
        reveal.addActionListener(
                ignored -> OpenDirectoryAction.open(parentPath(path).toString(), context.window()));
        menu.add(reveal);
        final JMenuItem terminal = new JMenuItem("Open terminal here");
        terminal.addActionListener(ignored -> actions.onOpenInTerminal().accept(parentPath(path)));
        menu.add(terminal);
        final JMenuItem copy = new JMenuItem("Copy path");
        copy.addActionListener(ignored -> CopyPathAction.copy(path.toAbsolutePath().toString()));
        menu.add(copy);
        UiFactory.showPopupMenu(menu, this, x, y);
    }

    private void showMenu(final MouseEvent event) {
        final TreePath treePath = this.getPathForLocation(event.getX(), event.getY());
        if (treePath == null) {
            return;
        }
        showMenuAt(treePath, event.getX(), event.getY());
    }

    private void installMouseListener() {
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2) {
                            final TreePath path = getPathForLocation(event.getX(), event.getY());
                            if (path == null) {
                                return;
                            }
                            final Object value =
                                    ((DefaultMutableTreeNode) path.getLastPathComponent())
                                            .getUserObject();
                            if (value instanceof Path selected) {
                                actions.onOpenSelectedPath().accept(selected);
                            }
                        }
                    }

                    @Override
                    public void mousePressed(final MouseEvent event) {
                        if (event.isPopupTrigger()) {
                            showMenu(event);
                        }
                    }

                    @Override
                    public void mouseReleased(final MouseEvent event) {
                        if (event.isPopupTrigger()) {
                            showMenu(event);
                        }
                    }
                });
    }

    private static Path parentPath(final Path path) {
        if (PathUtils.directory(path)) {
            return path;
        }
        final Path parent = path.getParent();
        return parent == null ? path : parent;
    }

    public record Actions(
            Consumer<Path> onOpenSelectedPath,
            Consumer<Path> onOpenInEditor,
            Consumer<Path> onOpenInTerminal,
            Function<Path, String> statusForPath) {}
}
