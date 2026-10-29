package com.jagent.desktop.services.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class Pty4jTtyConnector extends ProcessTtyConnector {
    private final PtyProcess process;
    private final Consumer<TerminalState> activity;
    private volatile long lastOutputAt;

    public Pty4jTtyConnector(final PtyProcess process, final Consumer<TerminalState> activity) {
        super(process, StandardCharsets.UTF_8);
        this.process = process;
        this.activity = activity;
    }

    @Override
    public int read(final char[] buffer, final int offset, final int length) throws IOException {
        final int count = super.read(buffer, offset, length);
        if (count > 0) {
            lastOutputAt = System.currentTimeMillis();
            activity.accept(TerminalState.WORKING);
        }
        return count;
    }

    @Override
    public void resize(final TermSize size) {
        if (isConnected()) {
            try {
                process.setWinSize(new com.pty4j.WinSize(size.getColumns(), size.getRows()));
            } catch (RuntimeException ignored) {
                // Some macOS PTY configurations reject a resize ioctl.
            }
        }
    }

    @Override
    public String getName() {
        return "Local";
    }

    public long lastOutputAt() {
        return lastOutputAt;
    }
}
