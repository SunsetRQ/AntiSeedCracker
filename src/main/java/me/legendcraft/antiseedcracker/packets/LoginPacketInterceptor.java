package me.legendcraft.antiseedcracker.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import org.bukkit.entity.Player;

public final class LoginPacketInterceptor extends PacketListenerAbstract {

    private final AntiSeedCrackerPlugin plugin;

    public LoginPacketInterceptor(AntiSeedCrackerPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.JOIN_GAME) return;
        if (!plugin.getPluginConfig().isInterceptLogin()) return;

        Object raw = event.getPlayer();
        if (!(raw instanceof Player player)) return;

        try {
            WrapperPlayServerJoinGame wrapper = new WrapperPlayServerJoinGame(event);
            wrapper.setHashedSeed(plugin.getSeedManager().getFakeSeed(player));
            event.markForReEncode(true);
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[AntiSeedCracker] Failed to spoof login seed for "
                    + player.getName() + ": " + e.getMessage());
        }
    }
}
