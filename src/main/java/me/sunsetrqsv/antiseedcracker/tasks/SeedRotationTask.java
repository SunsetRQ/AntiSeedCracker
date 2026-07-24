package me.sunsetrqsv.antiseedcracker.tasks;

import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import me.sunsetrqsv.antiseedcracker.scheduler.CancelableTask;
import me.sunsetrqsv.antiseedcracker.scheduler.FoliaSchedulerUtil;

public final class SeedRotationTask implements Runnable {

    private final AntiSeedCrackerPlugin plugin;
    private volatile CancelableTask handle;

    public SeedRotationTask(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (handle != null) {
            handle.cancel();
        }
        long periodMs = plugin.getPluginConfig().getSeedRotationIntervalSeconds() * 1000L;
        handle = FoliaSchedulerUtil.scheduleAsyncRepeating(plugin, this, periodMs, periodMs);
    }

    public void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    @Override
    public void run() {
        int count = plugin.getSeedManager().rotateAll();
        if (count > 0) {
            plugin.getLogger().info(
                    "[AntiSeedCracker] Rotated fake seeds for " + count + " player(s).");
        }
    }
}
