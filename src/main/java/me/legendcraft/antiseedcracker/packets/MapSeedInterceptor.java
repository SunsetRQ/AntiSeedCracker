package me.legendcraft.antiseedcracker.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.mapdecoration.MapDecorationType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData.MapDecoration;
import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import me.legendcraft.antiseedcracker.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.List;

public final class MapSeedInterceptor extends PacketListenerAbstract {

    private static final SecureRandom RNG = new SecureRandom();

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

            boolean modified = false;
            for (MapDecoration deco : decorations) {
                if (isStructureDecoration(deco.getType())) {
                    deco.setX((byte) (RNG.nextInt(256) - 128));
                    deco.setY((byte) (RNG.nextInt(256) - 128));
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

    private static boolean isStructureDecoration(MapDecorationType type) {
        try {
            String name = type.getName().toString().toUpperCase();
            return name.contains("MANSION")
                    || name.contains("MONUMENT")
                    || name.contains("RED_X")
                    || name.contains("TREASURE")
                    || name.contains("TRIAL_CHAMBERS")
                    || name.contains("VILLAGE")
                    || name.contains("PILLAGER")
                    || name.contains("BASTION")
                    || name.contains("JUNGLE_TEMPLE")
                    || name.contains("SWAMP_HUT");
        } catch (Exception ignored) {
            return false;
        }
    }
}
