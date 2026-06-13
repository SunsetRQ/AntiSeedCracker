package me.legendcraft.antiseedcracker.tasks;

import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import me.legendcraft.antiseedcracker.scheduler.CancelableTask;
import me.legendcraft.antiseedcracker.scheduler.FoliaSchedulerUtil;
import org.bukkit.World;

import java.util.List;

public final class SeedBroadcastTask implements Runnable {

    public static final long PERIOD_MS = 1_000L;

    private final AntiSeedCrackerPlugin plugin;
    private volatile CancelableTask handle;

    public SeedBroadcastTask(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (handle != null) {
            handle.cancel();
        }
        handle = FoliaSchedulerUtil.scheduleAsyncRepeating(
                plugin, this, PERIOD_MS, PERIOD_MS);
    }

    public void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    @Override
    public void run() {
        if (plugin.getPluginConfig().isPluginApiProtectionEnabled()
                && plugin.getWorldSeedInterceptor().isAvailable()) {

            List<World> worlds = plugin.getServer().getWorlds();
            for (World world : worlds) {
                long fakeSeed = plugin.getSeedManager().assignWorldFakeSeed(world);
                plugin.getWorldSeedInterceptor().patchWorld(world, fakeSeed);
            }
        }

        plugin.getSeedManager().ensureAllPlayersHaveSeeds(
                plugin.getServer().getOnlinePlayers());
    }
}
