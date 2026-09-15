package com.jagent.desktop.services.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class FakePtyProcess extends PtyProcess {
    private final long processId;
    private final ByteArrayOutputStream input = new ByteArrayOutputStream();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile InputStream outputStream = new ByteArrayInputStream(new byte[0]);
    private volatile WinSize winSize = new WinSize(80, 24);
    private volatile boolean alive = true;
    private volatile int exitCode;

    /* package */ FakePtyProcess(final long processId) {
        super();
        this.processId = processId;
    }

    @Override
    public OutputStream getOutputStream() {
        return input;
    }

    @Override
    public InputStream getInputStream() {
        return outputStream;
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() throws InterruptedException {
        finished.await();
        return exitCode;
    }

    @Override
    public boolean waitFor(final long timeout, final TimeUnit unit) throws InterruptedException {
        return finished.await(timeout, unit);
    }

    @Override
    public int exitValue() {
        if (alive) {
            throw new IllegalThreadStateException("Process is still running");
        }
        return exitCode;
    }

    @Override
    public void destroy() {
        complete(143);
    }

    @Override
    public Process destroyForcibly() {
        complete(137);
        return this;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public long pid() {
        return processId;
    }

    @Override
    public void setWinSize(final WinSize winSize) {
        this.winSize = winSize;
    }

    @Override
    public WinSize getWinSize() {
        return winSize;
    }

    /* package */ void emit(final String text) {
        output.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        outputStream = new ByteArrayInputStream(output.toByteArray());
    }

    /* package */ String submittedInput() {
        return input.toString(StandardCharsets.UTF_8);
    }

    /* package */ void exit(final int code) {
        complete(code);
    }

    private void complete(final int code) {
        if (!alive) {
            return;
        }
        alive = false;
        exitCode = code;
        finished.countDown();
    }
}

class StubTerminalRuntime extends TerminalRuntime {
    private final FakePtyProcess fakeProcess;
    private final IOException launchFailure;

    /* package */ StubTerminalRuntime(
            final String command,
            final Path directory,
            final String historyFile,
            final FakePtyProcess fakeProcess) {
        this(command, directory, historyFile, fakeProcess, null);
    }

    /* package */ StubTerminalRuntime(
            final String command,
            final Path directory,
            final String historyFile,
            final FakePtyProcess fakeProcess,
            final IOException launchFailure) {
        super(command, directory, historyFile);
        this.fakeProcess = fakeProcess;
        this.launchFailure = launchFailure;
    }

    @Override
    protected PtyProcess launchProcess() throws IOException {
        if (launchFailure != null) {
            throw launchFailure;
        }
        return fakeProcess;
    }

    /* package */ FakePtyProcess fakeProcess() {
        return fakeProcess;
    }
}
