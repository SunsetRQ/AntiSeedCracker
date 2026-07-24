package me.sunsetrqsv.antiseedcracker.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.sunsetrqsv.antiseedcracker.util.PlatformUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class FoliaSchedulerUtil {

    private FoliaSchedulerUtil() {}

    public static CancelableTask scheduleAsyncRepeating(Plugin plugin, Runnable task,
                                                         long initialDelayMs, long periodMs) {
        if (PlatformUtil.IS_PAPER) {
            ScheduledTask handle = plugin.getServer().getAsyncScheduler()
                    .runAtFixedRate(plugin, t -> task.run(), initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            return handle::cancel;
        }
        long delayTicks  = Math.max(1L, initialDelayMs / 50L);
        long periodTicks = Math.max(1L, periodMs / 50L);
        org.bukkit.scheduler.BukkitTask bt = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return bt::cancel;
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (PlatformUtil.IS_PAPER) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void runAtChunk(Plugin plugin, World world, int chunkX, int chunkZ,
                                   Runnable task) {
        if (PlatformUtil.IS_FOLIA) {
            plugin.getServer().getRegionScheduler()
                    .run(plugin, world, chunkX, chunkZ, t -> task.run());
            return;
        }
        if (PlatformUtil.IS_PAPER) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (PlatformUtil.IS_FOLIA) {
            plugin.getServer().getRegionScheduler()
                    .run(plugin, location, t -> task.run());
            return;
        }
        if (PlatformUtil.IS_PAPER) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runAtLocationDelayed(Plugin plugin, Location location,
                                             Runnable task, long delayTicks) {
        if (PlatformUtil.IS_FOLIA) {
            plugin.getServer().getRegionScheduler()
                    .runDelayed(plugin, location, t -> task.run(), delayTicks);
            return;
        }
        if (PlatformUtil.IS_PAPER) {
            plugin.getServer().getGlobalRegionScheduler()
                    .runDelayed(plugin, t -> task.run(), delayTicks);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        if (PlatformUtil.IS_FOLIA) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
            return;
        }
        if (PlatformUtil.IS_PAPER) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (PlatformUtil.IS_PAPER) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (PlatformUtil.IS_PAPER) {
            plugin.getServer().getGlobalRegionScheduler()
                    .runDelayed(plugin, t -> task.run(), delayTicks);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static CancelableTask scheduleGlobalRepeating(Plugin plugin, Runnable task,
                                                          long initialDelayTicks,
                                                          long periodTicks) {
        if (PlatformUtil.IS_PAPER) {
            ScheduledTask handle = plugin.getServer().getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, t -> task.run(), initialDelayTicks, periodTicks);
            return handle::cancel;
        }
        org.bukkit.scheduler.BukkitTask bt = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        return bt::cancel;
    }
}
