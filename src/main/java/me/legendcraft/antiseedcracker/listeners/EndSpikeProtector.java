package me.legendcraft.antiseedcracker.listeners;

import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import me.legendcraft.antiseedcracker.scheduler.FoliaSchedulerUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.boss.DragonBattle;
import org.bukkit.boss.DragonBattle.RespawnPhase;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.security.SecureRandom;
import java.util.Arrays;

public final class EndSpikeProtector implements Listener {

    private static final int[] VANILLA_HEIGHTS = {76, 79, 82, 85, 88, 91, 94, 97, 100, 103};
    private static final int CAP_MIN = 65;
    private static final int CAP_MAX = 120;

    private static final double[] PILLAR_ANGLES;
    static {
        PILLAR_ANGLES = new double[10];
        for (int i = 0; i < 10; i++) {
            PILLAR_ANGLES[i] = 2.0 * (-Math.PI + Math.PI * 0.1 * i);
        }
    }

    private static final SecureRandom RNG = new SecureRandom();

    private final AntiSeedCrackerPlugin plugin;
    private final NamespacedKey modifiedKey;

    public EndSpikeProtector(AntiSeedCrackerPlugin plugin) {
        this.plugin      = plugin;
        this.modifiedKey = new NamespacedKey(plugin, "asc_spike_modified");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        if (!shouldProcess(world)) return;
        triggerModification(world);
    }

    public void triggerModification(World world) {
        if (!shouldProcess(world)) return;
        scheduleModification(world);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalPlace(EntityPlaceEvent event) {
        if (event.getEntityType() != EntityType.END_CRYSTAL) return;
        World world = event.getEntity().getWorld();
        if (!shouldProcess(world)) return;

        Block placedOn = event.getBlock();
        if (placedOn == null || placedOn.getType() != Material.BEDROCK) return;
        if (Math.abs(placedOn.getX()) > 3 || Math.abs(placedOn.getZ()) > 3) return;

        world.getPersistentDataContainer().set(modifiedKey, PersistentDataType.BOOLEAN, false);
        schedulePollForRespawnEnd(world, 0);
    }

    private boolean shouldProcess(World world) {
        if (world.getEnvironment() != Environment.THE_END) return false;
        if (!plugin.getPluginConfig().isEndSpikesEnabled()) return false;
        return plugin.getPluginConfig().getEndSpikeWorlds().contains(world.getName());
    }

    private void scheduleModification(World world) {
        Boolean alreadyModified = world.getPersistentDataContainer()
                .getOrDefault(modifiedKey, PersistentDataType.BOOLEAN, false);
        if (alreadyModified) return;
        world.getPersistentDataContainer().set(modifiedKey, PersistentDataType.BOOLEAN, true);

        if (!plugin.getPluginConfig().isEndSpikesModifyWorld()) return;

        int[] heights = Arrays.copyOf(VANILLA_HEIGHTS, VANILLA_HEIGHTS.length);
        for (int i = heights.length - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int tmp = heights[i]; heights[i] = heights[j]; heights[j] = tmp;
        }
        for (int i = 0; i < VANILLA_HEIGHTS.length; i++) {
            modifyPillarCap(world, i, heights[i]);
        }
    }

    private void modifyPillarCap(World world, int index, int newHeight) {
        double x = 42.0 * Math.cos(PILLAR_ANGLES[index]);
        double z = 42.0 * Math.sin(PILLAR_ANGLES[index]);
        int cx = (int) Math.floor(x) >> 4;
        int cz = (int) Math.floor(z) >> 4;

        FoliaSchedulerUtil.runAtChunk(plugin, world, cx, cz, () -> {
            Block cap = null;
            for (int y = CAP_MAX; y >= CAP_MIN; y--) {
                Block b = world.getBlockAt((int) Math.round(x), y, (int) Math.round(z));
                if (b.getType() == Material.BEDROCK) {
                    cap = b;
                    break;
                }
            }
            if (cap == null) return;

            cap.setType(Material.OBSIDIAN, false);

            Block newCap = world.getBlockAt(cap.getX(), newHeight, cap.getZ());
            newCap.setType(Material.BEDROCK, false);
        });
    }

    private void schedulePollForRespawnEnd(World world, int attemptCount) {
        if (attemptCount > 300) {
            scheduleModification(world);
            return;
        }
        FoliaSchedulerUtil.runGlobalDelayed(plugin, () -> {
            DragonBattle battle = world.getEnderDragonBattle();
            RespawnPhase phase = (battle == null) ? null : battle.getRespawnPhase();
            boolean summoning = phase == RespawnPhase.START
                    || phase == RespawnPhase.PREPARING_TO_SUMMON_PILLARS
                    || phase == RespawnPhase.SUMMONING_PILLARS;
            if (summoning) {
                schedulePollForRespawnEnd(world, attemptCount + 1);
            } else {
                scheduleModification(world);
            }
        }, 20L);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
