package me.legendcraft.antiseedcracker.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import org.bukkit.entity.Player;

public final class RespawnPacketInterceptor extends PacketListenerAbstract {

    private final AntiSeedCrackerPlugin plugin;

    public RespawnPacketInterceptor(AntiSeedCrackerPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.RESPAWN) return;
        if (!plugin.getPluginConfig().isInterceptRespawn()) return;

        Object raw = event.getPlayer();
        if (!(raw instanceof Player player)) return;

        try {
            WrapperPlayServerRespawn wrapper = new WrapperPlayServerRespawn(event);
            wrapper.setHashedSeed(plugin.getSeedManager().getFakeSeed(player));
            event.markForReEncode(true);
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[AntiSeedCracker] Failed to spoof respawn seed for "
                    + player.getName() + ": " + e.getMessage());
        }
    }
}
