package me.legendcraft.antiseedcracker.database;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class DatabaseManager {

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String            SEP       = "\t";
    private static final int               QUEUE_CAP = 2_000;

    private final Path   logDir;
    private final Logger log;

    private final LinkedBlockingQueue<Runnable> writeQueue =
            new LinkedBlockingQueue<>(QUEUE_CAP);

    private Thread           writerThread;
    private volatile boolean running = false;

    public DatabaseManager(File dataFolder, Logger log) {
        this.logDir = dataFolder.toPath().resolve("logs");
        this.log    = log;
    }

    public void init() throws IOException {
        Files.createDirectories(logDir);
        startWriterThread();
        log.info("[AntiSeedCracker] Audit log initialised at " + logDir.toAbsolutePath());
    }

    public void close() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void logEvent(EventType type,
                         String playerUuid,
                         String playerName,
                         String worldName,
                         String detail) {
        final long   ts   = System.currentTimeMillis();
        final String date = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        boolean offered = writeQueue.offer(
                () -> doAppend(type, playerUuid, playerName, worldName, detail, ts, date));
        if (!offered) {
            log.fine("[ASC-Log] Write queue full — event dropped: " + type);
        }
    }

    public Map<String, Long> getStats() {
        Map<String, Long> counts = new HashMap<>();
        try (DirectoryStream<Path> ds =
                     Files.newDirectoryStream(logDir, "events-*.log")) {
            for (Path file : ds) {
                try (BufferedReader br =
                             Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.isBlank()) continue;
                        int first  = line.indexOf(SEP);
                        int second = first >= 0 ? line.indexOf(SEP, first + 1) : -1;
                        if (first >= 0 && second > first) {
                            counts.merge(line.substring(first + 1, second), 1L, Long::sum);
                        }
                    }
                } catch (IOException ignored) { }
            }
        } catch (IOException e) {
            log.warning("[ASC-Log] Stats scan failed: " + e.getMessage());
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    public int pruneOldEvents(int maxAgeDays) {
        long cutoffMs = System.currentTimeMillis() - ((long) maxAgeDays * 86_400_000L);
        int  removed  = 0;
        try (DirectoryStream<Path> ds =
                     Files.newDirectoryStream(logDir, "events-*.log")) {
            for (Path file : ds) {
                try {
                    if (Files.getLastModifiedTime(file).toMillis() < cutoffMs) {
                        Files.deleteIfExists(file);
                        removed++;
                    }
                } catch (IOException ignored) { }
            }
        } catch (IOException e) {
            log.warning("[ASC-Log] Prune failed: " + e.getMessage());
        }
        if (removed > 0) {
            log.info("[AntiSeedCracker] Pruned " + removed + " old audit log file(s).");
        }
        return removed;
    }

    private void startWriterThread() {
        running = true;
        writerThread = new Thread(() -> {
            while (running || !writeQueue.isEmpty()) {
                try {
                    Runnable task = writeQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Runnable remaining;
                    while ((remaining = writeQueue.poll()) != null) {
                        remaining.run();
                    }
                    break;
                }
            }
        }, "ASC-AuditLog-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void doAppend(EventType type,
                           String uuid, String name, String world, String detail,
                           long ts, String date) {
        Path file = logDir.resolve("events-" + date + ".log");
        String line = ts                  + SEP
                + type.name()             + SEP
                + sanitise(uuid)          + SEP
                + sanitise(name)          + SEP
                + sanitise(world)         + SEP
                + sanitise(detail);
        try (BufferedWriter bw = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            log.warning("[ASC-Log] Write failed: " + e.getMessage());
        }
    }

    private static String sanitise(String s) {
        if (s == null) return "";
        return s.replace("\t", " ").replace("\r", "").replace("\n", " ");
    }

    public enum EventType {
        SEED_COMMAND_BLOCKED,
        LOCATE_SPOOFED,
        EYE_REDIRECTED,
        TREASURE_MAP_SCRAMBLED,
        SEED_PACKET_SPOOFED,
        END_SPIKE_PROTECTED,
        END_CITY_PROTECTED
    }
}
