package me.legendcraft.antiseedcracker.listeners;

import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public final class PlayerSessionListener implements Listener {

    private static final Pattern LOCATE_PATTERN =
            Pattern.compile("^/?(?:minecraft:)?locate(?:\\s+(\\w+))?(?:\\s+([\\w:]+))?\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private static final SecureRandom RNG = new SecureRandom();

    private final AntiSeedCrackerPlugin plugin;

    public PlayerSessionListener(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        initPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getSeedManager().removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.getSeedManager().forgetWorld(event.getWorld().getUID());
    }

    public void initPlayer(Player player) {
        plugin.getSeedManager().assignSeed(player);
        plugin.getSeedManager().assignFakeStronghold(
                player,
                plugin.getPluginConfig().getFakeStrongholdMinDist(),
                plugin.getPluginConfig().getFakeStrongholdMaxDist()
        );
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getPluginConfig().isSpoofLocateEnabled()) return;

        String message = event.getMessage().trim();
        if (!LOCATE_PATTERN.matcher(message).matches()) return;

        event.setCancelled(true);

        Player player       = event.getPlayer();
        String structureArg = extractStructureArg(message);
        int[]  fakeCoords   = buildFakeLocateCoords(player, structureArg);
        int    fakeX        = fakeCoords[0];
        int    fakeZ        = fakeCoords[1];

        double dx   = fakeX - player.getLocation().getX();
        double dz   = fakeZ - player.getLocation().getZ();
        int    dist = (int) Math.sqrt(dx * dx + dz * dz);

        String displayName = formatStructureName(structureArg);
        player.sendMessage(
                Component.text("The nearest " + displayName + " is at ", NamedTextColor.WHITE)
                        .append(Component.text("[" + fakeX + ", ~, " + fakeZ + "]", NamedTextColor.GREEN))
                        .append(Component.text(" (approximately ", NamedTextColor.WHITE))
                        .append(Component.text(String.valueOf(dist), NamedTextColor.GREEN))
                        .append(Component.text(" blocks away)", NamedTextColor.WHITE)));
    }

    private String extractStructureArg(String command) {
        String[] tokens = command.replaceFirst("^/", "").split("\\s+");
        int startIdx = 1;
        if (tokens.length > startIdx) {
            String t1 = tokens[startIdx].toLowerCase();
            if (t1.equals("structure") || t1.equals("biome")) startIdx = 2;
        }
        if (tokens.length > startIdx) return tokens[startIdx].toLowerCase();
        return "minecraft:stronghold";
    }

    private int[] buildFakeLocateCoords(Player player, String structureArg) {
        if (structureArg.contains("stronghold")) {
            int[] fakeStronghold = plugin.getSeedManager().getFakeStronghold(player.getUniqueId());
            if (fakeStronghold != null) return fakeStronghold;
        }
        int    maxOffset = plugin.getPluginConfig().getSpoofLocateMaxOffset();
        int    minOffset = maxOffset / 4;
        double angle     = RNG.nextDouble() * 2.0 * Math.PI;
        int    dist      = minOffset + RNG.nextInt(Math.max(1, maxOffset - minOffset + 1));
        int    baseX     = (int) player.getLocation().getX();
        int    baseZ     = (int) player.getLocation().getZ();
        int    x         = ((baseX + (int) Math.round(Math.cos(angle) * dist)) >> 4) << 4;
        int    z         = ((baseZ + (int) Math.round(Math.sin(angle) * dist)) >> 4) << 4;
        return new int[]{x, z};
    }

    private String formatStructureName(String id) {
        String   name  = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
