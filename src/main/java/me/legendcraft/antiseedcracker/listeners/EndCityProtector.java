package me.legendcraft.antiseedcracker.listeners;

import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import me.legendcraft.antiseedcracker.scheduler.FoliaSchedulerUtil;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;

public final class EndCityProtector implements Listener {

    private final NamespacedKey modifiedKey;
    private final AntiSeedCrackerPlugin plugin;

    public EndCityProtector(AntiSeedCrackerPlugin plugin) {
        this.plugin      = plugin;
        this.modifiedKey = new NamespacedKey(plugin, "asc_city_modified");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getPluginConfig().isEndCitiesEnabled()) return;

        String worldName = event.getWorld().getName();
        if (!plugin.getPluginConfig().getEndCityWorlds().contains(worldName)) return;

        Chunk chunk = event.getChunk();

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (pdc.has(modifiedKey, PersistentDataType.BYTE)) return;

        FoliaSchedulerUtil.runAtChunk(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> {
            if (!chunk.isLoaded()) return;

            try {
                Collection<GeneratedStructure> structures =
                        chunk.getStructures(Structure.END_CITY);

                if (structures.isEmpty()) return;

                if (plugin.getPluginConfig().isEndCitiesModifyWorld()) {
                    replaceGlassInChunk(chunk);
                }

                chunk.getPersistentDataContainer()
                        .set(modifiedKey, PersistentDataType.BYTE, (byte) 1);

            } catch (Exception e) {
                plugin.getLogger().warning("[AntiSeedCracker] EndCityProtector scan error"
                        + " at chunk (" + chunk.getX() + "," + chunk.getZ() + "): " + e.getMessage());
            }
        });
    }

    private void replaceGlassInChunk(Chunk chunk) {
        World world  = chunk.getWorld();
        int   minY   = world.getMinHeight();
        int   maxY   = world.getMaxHeight();
        int   baseX  = chunk.getX() << 4;
        int   baseZ  = chunk.getZ() << 4;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = world.getBlockAt(baseX + dx, y, baseZ + dz);
                    if (block.getType() == Material.MAGENTA_STAINED_GLASS) {
                        block.setType(Material.PURPUR_BLOCK, false);
                    }
                }
            }
        }
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
