package me.legendcraft.antiseedcracker.listeners;

import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import me.legendcraft.antiseedcracker.database.DatabaseManager;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureReconMonitor implements Listener {

    private static final Set<Structure> TRACKED_STRUCTURES = buildTrackedStructures();

    private static Set<Structure> buildTrackedStructures() {
        Set<Structure> set = new HashSet<>();
        set.add(Structure.SHIPWRECK);
        set.add(Structure.SHIPWRECK_BEACHED);
        set.add(Structure.DESERT_PYRAMID);
        set.add(Structure.JUNGLE_PYRAMID);
        set.add(Structure.SWAMP_HUT);
        set.add(Structure.PILLAGER_OUTPOST);
        set.add(Structure.MONUMENT);
        set.add(Structure.IGLOO);
        set.add(Structure.END_CITY);
        set.add(Structure.ANCIENT_CITY);
        return set;
    }

    private final AntiSeedCrackerPlugin plugin;
    private final Map<UUID, Set<Long>>          visitedChunks       = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>>        visitTimestamps     = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Boolean>> chunkQualifiesCache = new ConcurrentHashMap<>();

    public StructureReconMonitor(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getPluginConfig().isStructureReconMonitorEnabled()) return;

        int fromChunkX = event.getFrom().getBlockX() >> 4;
        int fromChunkZ = event.getFrom().getBlockZ() >> 4;
        int toChunkX   = event.getTo().getBlockX() >> 4;
        int toChunkZ   = event.getTo().getBlockZ() >> 4;
        if (fromChunkX == toChunkX && fromChunkZ == toChunkZ) return;

        UUID worldUid = event.getTo().getWorld().getUID();
        long chunkKey = (((long) toChunkX) << 32) ^ (toChunkZ & 0xFFFFFFFFL);

        Map<Long, Boolean> worldCache = chunkQualifiesCache.computeIfAbsent(
                worldUid, k -> new ConcurrentHashMap<>());
        Boolean qualifies = worldCache.get(chunkKey);
        if (qualifies == null) {
            Chunk chunk = event.getTo().getChunk();
            Collection<GeneratedStructure> structures;
            try {
                structures = chunk.getStructures();
            } catch (Exception e) {
                return;
            }
            qualifies = false;
            for (GeneratedStructure s : structures) {
                if (TRACKED_STRUCTURES.contains(s.getStructure())) {
                    qualifies = true;
                    break;
                }
            }
            worldCache.put(chunkKey, qualifies);
        }
        if (!qualifies) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Set<Long> visited = visitedChunks.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        if (!visited.add(chunkKey)) return;

        int  windowSeconds = plugin.getPluginConfig().getStructureReconWindowSeconds();
        int  threshold     = plugin.getPluginConfig().getStructureReconThreshold();
        long now           = System.currentTimeMillis();
        long windowStart   = now - (windowSeconds * 1000L);

        Deque<Long> timestamps = visitTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(now);
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= threshold) {
                timestamps.clear();
                flagPlayer(player);
            }
        }
    }

    private void flagPlayer(Player player) {
        plugin.getLogger().warning("[AntiSeedCracker] Structure reconnaissance pattern detected for "
                + player.getName() + " — visited multiple seed-relevant structures in a short "
                + "window. This does not block the player (per design); review manually if "
                + "seed-cracking tool use is suspected.");

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null && plugin.getPluginConfig().isDatabaseLogEvents()) {
            db.logEvent(DatabaseManager.EventType.STRUCTURE_RECON_FLAGGED,
                    player.getUniqueId().toString(),
                    player.getName(),
                    player.getWorld().getName(),
                    "Rapid multi-structure visitation pattern");
        }
    }

    public void removePlayer(UUID uuid) {
        visitedChunks.remove(uuid);
        visitTimestamps.remove(uuid);
    }

    public void forgetWorld(UUID worldUid) {
        chunkQualifiesCache.remove(worldUid);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
