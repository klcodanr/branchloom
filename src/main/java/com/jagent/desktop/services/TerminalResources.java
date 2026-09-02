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
        final Map<Long, Long> cpu = cpuTime(processes.keySet());
        for (final Map.Entry<ProcessTarget, List<ProcessHandle>> entry : trees.entrySet()) {
            long cpuNanos = 0;
            long memoryBytes = 0;
            for (final ProcessHandle process : entry.getValue()) {
                final Long psCpuMillis = cpu.get(process.pid());
                cpuNanos +=
                        psCpuMillis == null
                                ? process.info().totalCpuDuration().orElse(Duration.ZERO).toNanos()
                                : TimeUnit.MILLISECONDS.toNanos(psCpuMillis);
                memoryBytes += memory.getOrDefault(process.pid(), 0L);
            }
            usage.add(
                    new Usage(
                            entry.getKey().name(),
                            entry.getKey().pid(),
                            entry.getValue().size(),
                            Duration.ofNanos(cpuNanos).toMillis(),
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

    private static Map<Long, Long> cpuTime(final Collection<Long> pids) {
        if (pids.isEmpty() || PlatformCommands.isWindows()) {
            return Map.of();
        }
        final List<String> command = new ArrayList<>(List.of("ps", "-o", "pid=,time=", "-p"));
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
                    result.put(Long.parseLong(fields[0]), parseCpuTime(fields[1]));
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

    private static long parseCpuTime(final String value) {
        final String[] fields = value.split(":");
        final String seconds = fields[fields.length - 1];
        long totalSeconds = 0;
        if (fields.length > 1) {
            totalSeconds += Long.parseLong(fields[fields.length - 2]) * 60;
        }
        if (fields.length > 2) {
            final String[] days = fields[0].split("-");
            totalSeconds += Long.parseLong(days[days.length - 1]) * 3600;
            if (days.length > 1) {
                totalSeconds += Long.parseLong(days[0]) * 24 * 3600;
            }
        }
        return totalSeconds * 1_000 + Math.round(Double.parseDouble(seconds) * 1_000);
    }

    public record ProcessTarget(String name, long pid) {}

    public record Sample(List<Usage> terminals, boolean memoryAvailable) {}

    public record Usage(
            String name, long pid, int processCount, long cpuMillis, long memoryBytes) {}
}
