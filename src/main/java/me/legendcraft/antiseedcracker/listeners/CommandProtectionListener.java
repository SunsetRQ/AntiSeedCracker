package me.legendcraft.antiseedcracker.listeners;

import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.TabCompleteEvent;

public final class CommandProtectionListener implements Listener {

    private final AntiSeedCrackerPlugin plugin;

    public CommandProtectionListener(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        if (!isSeedCommand(raw) && !isExtraBlocked(raw)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.getPluginConfig().getMessageSeedBlockedPlayer());
        plugin.getLogger().warning(
                "[AntiSeedCracker] Seed command blocked for player "
                + event.getPlayer().getName() + ": " + raw);

        me.legendcraft.antiseedcracker.database.DatabaseManager db =
                plugin.getDatabaseManager();
        if (db != null && plugin.getPluginConfig().isDatabaseLogEvents()) {
            db.logEvent(
                    me.legendcraft.antiseedcracker.database.DatabaseManager.EventType
                            .SEED_COMMAND_BLOCKED,
                    event.getPlayer().getUniqueId().toString(),
                    event.getPlayer().getName(),
                    event.getPlayer().getWorld().getName(),
                    raw);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onServerCommand(ServerCommandEvent event) {
        String raw = event.getCommand().trim();
        if (!isSeedCommand("/" + raw) && !isExtraBlocked("/" + raw)) return;

        event.setCancelled(true);
        event.getSender().sendMessage(plugin.getPluginConfig().getMessageSeedBlockedConsole());
        plugin.getLogger().warning(
                "[AntiSeedCracker] Console seed command blocked: " + raw);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onRemoteServerCommand(RemoteServerCommandEvent event) {
        String raw = event.getCommand().trim();
        if (!isSeedCommand("/" + raw) && !isExtraBlocked("/" + raw)) return;

        event.setCancelled(true);
        event.getSender().sendMessage(plugin.getPluginConfig().getMessageSeedBlockedConsole());
        plugin.getLogger().warning(
                "[AntiSeedCracker] RCON seed command blocked: " + raw);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        java.util.List<String> extra = plugin.getPluginConfig().getExtraBlockedCommands();
        event.getCompletions().removeIf(c -> {
            String lower = c.toLowerCase();
            if (lower.startsWith("/")) lower = lower.substring(1);
            int colon = lower.indexOf(':');
            if (colon >= 0) lower = lower.substring(colon + 1);
            return lower.equals("seed")
                    || lower.equals("getseed")
                    || lower.equals("worldseed")
                    || extra.contains(lower);
        });
    }

    private static boolean isSeedCommand(String raw) {
        String lower = raw.toLowerCase();
        if (lower.startsWith("/")) lower = lower.substring(1);
        int colon = lower.indexOf(':');
        if (colon >= 0) lower = lower.substring(colon + 1);
        return lower.equals("seed")
                || lower.startsWith("seed ")
                || lower.equals("getseed")
                || lower.startsWith("getseed ")
                || lower.equals("worldseed")
                || lower.startsWith("worldseed ");
    }

    private boolean isExtraBlocked(String raw) {
        String lower = raw.toLowerCase();
        if (lower.startsWith("/")) lower = lower.substring(1);
        int colon = lower.indexOf(':');
        if (colon >= 0) lower = lower.substring(colon + 1);
        String root = lower.split("\\s+")[0];
        return plugin.getPluginConfig().getExtraBlockedCommands().contains(root);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
