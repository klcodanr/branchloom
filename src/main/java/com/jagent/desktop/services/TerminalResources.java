package com.jagent.desktop.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class TerminalResources {
    private TerminalResources() {}

    public static Sample sample(final Collection<ProcessTarget> roots) {
        final List<Usage> usage = new ArrayList<>();
        final Map<Long, ProcessHandle> processes = new HashMap<>();
        final Map<ProcessTarget, List<ProcessHandle>> trees = new HashMap<>();
        for (final ProcessTarget root : roots) {
            final ProcessHandle rootHandle = ProcessHandle.of(root.pid()).orElse(null);
            if (rootHandle == null || !rootHandle.isAlive()) {
                continue;
            }
            final List<ProcessHandle> tree = new ArrayList<>();
            tree.add(rootHandle);
            rootHandle.descendants().filter(ProcessHandle::isAlive).forEach(tree::add);
            tree.forEach(process -> processes.put(process.pid(), process));
            trees.put(root, tree);
        }
        final Map<Long, Long> memory = residentMemory(processes.keySet());
        for (final Map.Entry<ProcessTarget, List<ProcessHandle>> entry : trees.entrySet()) {
            long cpuMillis = 0;
            long memoryBytes = 0;
            for (final ProcessHandle process : entry.getValue()) {
                cpuMillis += process.info().totalCpuDuration().orElse(Duration.ZERO).toMillis();
                memoryBytes += memory.getOrDefault(process.pid(), 0L);
            }
            usage.add(
                    new Usage(
                            entry.getKey().name(),
                            entry.getKey().pid(),
                            entry.getValue().size(),
                            cpuMillis,
                            memoryBytes));
        }
        return new Sample(usage, !memory.isEmpty());
    }

    private static Map<Long, Long> residentMemory(final Collection<Long> pids) {
        if (pids.isEmpty() || PlatformCommands.isWindows()) {
            return Map.of();
        }
        final List<String> command = new ArrayList<>(List.of("ps", "-o", "pid=,rss=", "-p"));
        command.add(String.join(",", pids.stream().map(Object::toString).toList()));
        try {
            final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return Map.of();
            }
            final Map<Long, Long> result = new HashMap<>();
            for (final String line : output.split("\\R")) {
                final String[] fields = line.trim().split("\\s+");
                if (fields.length == 2) {
                    result.put(Long.parseLong(fields[0]), Long.parseLong(fields[1]) * 1024);
                }
            }
            return result;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException | NumberFormatException ignored) {
            return Map.of();
        }
    }

    public record ProcessTarget(String name, long pid) {}

    public record Sample(List<Usage> terminals, boolean memoryAvailable) {}

    public record Usage(
            String name, long pid, int processCount, long cpuMillis, long memoryBytes) {}
}
