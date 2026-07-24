package me.sunsetrqsv.antiseedcracker.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.mapdecoration.MapDecorationType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData.MapDecoration;
import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import me.sunsetrqsv.antiseedcracker.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.util.List;

public final class MapSeedInterceptor extends PacketListenerAbstract {

    private final AntiSeedCrackerPlugin plugin;

    public MapSeedInterceptor(AntiSeedCrackerPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.MAP_DATA) return;
        if (!plugin.getPluginConfig().isTreasureMapProtectionEnabled()) return;

        try {
            WrapperPlayServerMapData wrapper = new WrapperPlayServerMapData(event);

            List<MapDecoration> decorations = wrapper.getDecorations();
            if (decorations == null || decorations.isEmpty()) return;

            int mapId = wrapper.getMapId();
            boolean modified = false;
            for (MapDecoration deco : decorations) {
                if (isStructureDecoration(deco.getType())) {
                    byte origX = deco.getX();
                    byte origY = deco.getY();
                    deco.setX(fakeCoordinate(mapId, deco.getType(), origX, origY, false));
                    deco.setY(fakeCoordinate(mapId, deco.getType(), origX, origY, true));
                    modified = true;
                }
            }

            if (modified) {
                wrapper.setDecorations(decorations);
                event.markForReEncode(true);

                DatabaseManager db = plugin.getDatabaseManager();
                if (db != null && plugin.getPluginConfig().isDatabaseLogEvents()) {
                    Object raw = event.getPlayer();
                    if (raw instanceof Player player) {
                        db.logEvent(DatabaseManager.EventType.TREASURE_MAP_SCRAMBLED,
                                player.getUniqueId().toString(),
                                player.getName(),
                                player.getWorld().getName(),
                                "Structure map marker scrambled");
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().fine(
                    "[AntiSeedCracker] MapSeedInterceptor skipped: " + e.getMessage());
        }
    }

    /**
     * Matches against the real vanilla map decoration type IDs (verified against
     * {@code org.bukkit.map.MapCursor.Type}: no "treasure", "pillager", or "bastion" type
     * exists at all — those three never matched anything. Buried treasure maps actually use
     * {@code TARGET_X}, which was previously missing here, so buried treasure locations
     * (one of this plugin's own named SeedCrackerX signals) were never actually scrambled.
     */
    private static boolean isStructureDecoration(MapDecorationType type) {
        try {
            String name = type.getName().toString().toUpperCase();
            return name.contains("MANSION")
                    || name.contains("MONUMENT")
                    || name.contains("TARGET_X")
                    || name.contains("RED_X")
                    || name.contains("TRIAL_CHAMBERS")
                    || name.contains("VILLAGE")
                    || name.contains("JUNGLE_TEMPLE")
                    || name.contains("SWAMP_HUT");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Derives a fake coordinate deterministically from the map ID, icon type, and real
     * position instead of drawing fresh randomness per packet. MAP_DATA resends the full
     * decoration list on every update while a player views the map; a fresh random value
     * each time made the icon visibly jitter/teleport around the map every tick, which is
     * a far more obvious "something is spoofing this" tell than a fixed wrong position.
     */
    private static byte fakeCoordinate(int mapId, MapDecorationType type, byte origX, byte origY, boolean axisY) {
        long h = mapId;
        h = h * 31 + type.getName().toString().hashCode();
        h = h * 31 + origX;
        h = h * 31 + origY;
        h = h * 31 + (axisY ? 1 : 0);
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (byte) h;
    }
}
