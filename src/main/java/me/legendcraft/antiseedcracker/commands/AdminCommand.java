package me.legendcraft.antiseedcracker.commands;

import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import me.legendcraft.antiseedcracker.scheduler.FoliaSchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "antiseedcracker.admin";

    private static final Component PREFIX =
            Component.text("[", NamedTextColor.DARK_GRAY)
                     .append(Component.text("ASC", NamedTextColor.AQUA))
                     .append(Component.text("] ", NamedTextColor.DARK_GRAY));

    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "status", "stats", "info");

    private final AntiSeedCrackerPlugin plugin;

    public AdminCommand(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(msg(Component.text(
                    "You do not have permission to use this command.", NamedTextColor.RED)));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "stats"  -> handleStats(sender);
            case "info"   -> handleInfo(sender);
            default       -> sendUsage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return Collections.emptyList();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage(msg(Component.text("Reloading AntiSeedCracker…", NamedTextColor.YELLOW)));
        try {
            plugin.reload();
            sender.sendMessage(msg(Component.text("Reload successful.", NamedTextColor.GREEN)));
        } catch (Exception e) {
            sender.sendMessage(msg(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED)));
            plugin.getLogger().severe("[AntiSeedCracker] Reload exception: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender) {
        boolean loginIntercepted   = plugin.getPluginConfig().isInterceptLogin();
        boolean respawnIntercepted = plugin.getPluginConfig().isInterceptRespawn();
        boolean rotationEnabled    = plugin.getPluginConfig().isSeedRotationEnabled();
        boolean spikesEnabled      = plugin.getPluginConfig().isEndSpikesEnabled();
        boolean citiesEnabled      = plugin.getPluginConfig().isEndCitiesEnabled();
        boolean eyeEnabled         = plugin.getPluginConfig().isEyeOfEnderEnabled();
        boolean locateEnabled      = plugin.getPluginConfig().isSpoofLocateEnabled();
        boolean pluginApiEnabled   = plugin.getPluginConfig().isPluginApiProtectionEnabled();
        boolean interceptorActive  = plugin.getWorldSeedInterceptor().isAvailable();
        boolean mapProtEnabled     = plugin.getPluginConfig().isTreasureMapProtectionEnabled();
        boolean dbEnabled          = plugin.getDatabaseManager() != null;
        int     trackedSeeds       = plugin.getSeedManager().trackedCount();

        sender.sendMessage(Component.empty());
        sender.sendMessage(msg(Component.text("━━ AntiSeedCracker v"
                + plugin.getPluginMeta().getVersion() + " by SunsetRQ7_ ━━",
                NamedTextColor.GOLD)));
        sender.sendMessage(statusLine("Login Packet Intercept",   loginIntercepted));
        sender.sendMessage(statusLine("Respawn Packet Intercept", respawnIntercepted));
        sender.sendMessage(statusLine("Seed Rotation",            rotationEnabled));
        sender.sendMessage(statusLine("End Spike Protection",     spikesEnabled));
        sender.sendMessage(statusLine("End City Protection",      citiesEnabled));
        sender.sendMessage(statusLine("Eye of Ender Redirect",    eyeEnabled));
        sender.sendMessage(statusLine("/locate Spoof",            locateEnabled));
        sender.sendMessage(statusLine("Plugin API Protection",    pluginApiEnabled && interceptorActive));
        sender.sendMessage(statusLine("Treasure Map Scramble",    mapProtEnabled));
        sender.sendMessage(statusLine("Database / Audit Log",     dbEnabled));
        sender.sendMessage(msg(
                Component.text("Tracked players: ", NamedTextColor.AQUA)
                         .append(Component.text(String.valueOf(trackedSeeds), NamedTextColor.WHITE))));
        sender.sendMessage(Component.empty());
    }

    private void handleStats(CommandSender sender) {
        me.legendcraft.antiseedcracker.database.DatabaseManager db =
                plugin.getDatabaseManager();
        if (db == null) {
            sender.sendMessage(msg(Component.text(
                    "Database is disabled or failed to initialise.", NamedTextColor.RED)));
            return;
        }
        sender.sendMessage(msg(Component.text("Loading stats…", NamedTextColor.YELLOW)));
        FoliaSchedulerUtil.runAsync(plugin, () -> {
            java.util.Map<String, Long> stats = db.getStats();
            FoliaSchedulerUtil.runGlobal(plugin, () -> {
                sender.sendMessage(Component.empty());
                sender.sendMessage(msg(Component.text(
                        "━━ Protection Statistics ━━", NamedTextColor.GOLD)));
                if (stats.isEmpty()) {
                    sender.sendMessage(msg(Component.text(
                            "No events recorded yet.", NamedTextColor.GRAY)));
                } else {
                    for (java.util.Map.Entry<String, Long> e : stats.entrySet()) {
                        String label = e.getKey().replace('_', ' ').toLowerCase();
                        label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
                        sender.sendMessage(
                                Component.text("  ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(label + ": ", NamedTextColor.GRAY))
                                .append(Component.text(String.valueOf(e.getValue()),
                                        NamedTextColor.WHITE)));
                    }
                }
                sender.sendMessage(Component.empty());
            });
        });
    }

    private void handleInfo(CommandSender sender) {
        me.legendcraft.antiseedcracker.util.UpdateChecker uc = plugin.getUpdateChecker();
        sender.sendMessage(Component.empty());
        sender.sendMessage(msg(Component.text(
                "━━ AntiSeedCracker Info ━━", NamedTextColor.GOLD)));
        sender.sendMessage(msg(Component.text("Version:  ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getPluginMeta().getVersion(),
                        NamedTextColor.WHITE))));
        sender.sendMessage(msg(Component.text("Author:   ", NamedTextColor.GRAY)
                .append(Component.text("SunsetRQ7_", NamedTextColor.AQUA))));
        sender.sendMessage(msg(Component.text("Platform: ", NamedTextColor.GRAY)
                .append(Component.text(
                        me.legendcraft.antiseedcracker.util.PlatformUtil.name(),
                        NamedTextColor.WHITE))));
        if (uc != null) {
            if (uc.isUpToDate()) {
                sender.sendMessage(msg(Component.text(
                        "Update:   ✔ Up to date", NamedTextColor.GREEN)));
            } else if (uc.getLatestVersion() != null) {
                sender.sendMessage(msg(Component.text(
                        "Update:   ✘ v" + uc.getLatestVersion()
                        + " available on Modrinth!", NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(msg(Component.text(
                        "Update:   not yet checked", NamedTextColor.GRAY)));
            }
        }
        sender.sendMessage(Component.empty());
    }

    private static Component msg(Component body) {
        return PREFIX.append(body);
    }

    private static Component statusLine(String label, boolean enabled) {
        Component status = enabled
                ? Component.text("✔ enabled",  NamedTextColor.GREEN)
                : Component.text("✘ disabled", NamedTextColor.RED);
        return Component.text("  ")
                .append(Component.text(label, NamedTextColor.GRAY))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(status);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(msg(
                Component.text("Usage: ", NamedTextColor.YELLOW)
                         .append(Component.text("/asc <reload|status|stats|info>",
                                 NamedTextColor.WHITE))));
    }
}
