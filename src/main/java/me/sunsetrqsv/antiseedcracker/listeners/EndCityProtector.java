package me.sunsetrqsv.antiseedcracker.listeners;

import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import me.sunsetrqsv.antiseedcracker.scheduler.FoliaSchedulerUtil;
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
import org.bukkit.util.BoundingBox;

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

                if (structures.isEmpty()) {
                    // Nothing in this chunk ever needs checking again - structures don't
                    // appear after generation, so this is permanently safe to skip.
                    chunk.getPersistentDataContainer()
                            .set(modifiedKey, PersistentDataType.BYTE, (byte) 1);
                    return;
                }

                if (plugin.getPluginConfig().isEndCitiesModifyWorld()) {
                    for (GeneratedStructure structure : structures) {
                        replaceGlassInBounds(chunk, structure.getBoundingBox());
                    }
                    chunk.getPersistentDataContainer()
                            .set(modifiedKey, PersistentDataType.BYTE, (byte) 1);
                }
                // else: an End City is here but modify_world is off - leave unmarked so a
                // future load re-checks this chunk in case modify_world gets enabled later.
                // Marking it "done" here without having done anything would permanently
                // skip this chunk even after the admin turns modify_world on.

            } catch (Exception e) {
                plugin.getLogger().warning("[AntiSeedCracker] EndCityProtector scan error"
                        + " at chunk (" + chunk.getX() + "," + chunk.getZ() + "): " + e.getMessage());
            }
        });
    }

    private void replaceGlassInBounds(Chunk chunk, BoundingBox bounds) {
        World world = chunk.getWorld();
        int   minY  = Math.max(world.getMinHeight(), (int) Math.floor(bounds.getMinY()));
        int   maxY  = Math.min(world.getMaxHeight(), (int) Math.ceil(bounds.getMaxY()));
        int   baseX = chunk.getX() << 4;
        int   baseZ = chunk.getZ() << 4;
        int   minX  = Math.max(baseX,     (int) Math.floor(bounds.getMinX()));
        int   maxX  = Math.min(baseX + 15, (int) Math.ceil(bounds.getMaxX()));
        int   minZ  = Math.max(baseZ,     (int) Math.floor(bounds.getMinZ()));
        int   maxZ  = Math.min(baseZ + 15, (int) Math.ceil(bounds.getMaxZ()));

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
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
