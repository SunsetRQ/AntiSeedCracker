package me.legendcraft.antiseedcracker;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import me.legendcraft.antiseedcracker.commands.AdminCommand;
import me.legendcraft.antiseedcracker.config.PluginConfig;
import me.legendcraft.antiseedcracker.database.DatabaseManager;
import me.legendcraft.antiseedcracker.listeners.CommandProtectionListener;
import me.legendcraft.antiseedcracker.listeners.EndCityProtector;
import me.legendcraft.antiseedcracker.listeners.EndSpikeProtector;
import me.legendcraft.antiseedcracker.listeners.EyeOfEnderProtector;
import me.legendcraft.antiseedcracker.listeners.PlayerSessionListener;
import me.legendcraft.antiseedcracker.packets.LoginPacketInterceptor;
import me.legendcraft.antiseedcracker.packets.MapSeedInterceptor;
import me.legendcraft.antiseedcracker.packets.RespawnPacketInterceptor;
import me.legendcraft.antiseedcracker.scheduler.FoliaSchedulerUtil;
import me.legendcraft.antiseedcracker.seed.SeedManager;
import me.legendcraft.antiseedcracker.seed.WorldSeedInterceptor;
import me.legendcraft.antiseedcracker.tasks.SeedBroadcastTask;
import me.legendcraft.antiseedcracker.tasks.SeedRotationTask;
import me.legendcraft.antiseedcracker.util.PlatformUtil;
import me.legendcraft.antiseedcracker.util.UpdateChecker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class AntiSeedCrackerPlugin extends JavaPlugin {

    private PluginConfig    pluginConfig;
    private SeedManager     seedManager;
    private DatabaseManager databaseManager;
    private UpdateChecker   updateChecker;

    private SeedBroadcastTask seedBroadcastTask;
    private SeedRotationTask  seedRotationTask;

    private WorldSeedInterceptor worldSeedInterceptor;

    private PlayerSessionListener     playerSessionListener;
    private CommandProtectionListener commandProtectionListener;
    private EyeOfEnderProtector       eyeOfEnderProtector;
    private EndSpikeProtector         endSpikeProtector;
    private EndCityProtector          endCityProtector;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .checkForUpdates(false)
                .reEncodeByDefault(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        getLogger().info("[AntiSeedCracker] Platform detected: " + PlatformUtil.name());

        saveDefaultConfig();
        pluginConfig = new PluginConfig(getConfig());

        seedManager = new SeedManager(getLogger());

        if (pluginConfig.isDatabaseEnabled()) {
            try {
                databaseManager = new DatabaseManager(getDataFolder(), getLogger());
                databaseManager.init();
                final int maxDays = pluginConfig.getDatabaseMaxEventAgeDays();
                FoliaSchedulerUtil.runAsync(this, () -> databaseManager.pruneOldEvents(maxDays));
            } catch (Exception e) {
                getLogger().warning(
                        "[AntiSeedCracker] Audit log init failed ("
                        + e.getMessage()
                        + ") — event logging disabled.");
                databaseManager = null;
            }
        }

        if (pluginConfig.isUpdateCheckerEnabled()) {
            updateChecker = new UpdateChecker(this, pluginConfig.getModrinthProjectId());
            updateChecker.checkAsync();
        }

        worldSeedInterceptor = new WorldSeedInterceptor(getLogger());

        seedBroadcastTask = new SeedBroadcastTask(this);
        seedBroadcastTask.start();

        PacketEvents.getAPI().getEventManager().registerListeners(
                new LoginPacketInterceptor(this),
                new RespawnPacketInterceptor(this),
                new MapSeedInterceptor(this)
        );
        PacketEvents.getAPI().init();

        registerListeners();

        seedRotationTask = new SeedRotationTask(this);
        if (pluginConfig.isSeedRotationEnabled()) seedRotationTask.start();

        AdminCommand adminCmd = new AdminCommand(this);
        Objects.requireNonNull(getCommand("asc")).setExecutor(adminCmd);
        Objects.requireNonNull(getCommand("asc")).setTabCompleter(adminCmd);

        for (Player player : getServer().getOnlinePlayers()) {
            playerSessionListener.initPlayer(player);
        }

        getLogger().info("[AntiSeedCracker] Enabled — real seed is protected.");
    }

    @Override
    public void onDisable() {
        if (seedBroadcastTask  != null) seedBroadcastTask.stop();
        if (seedRotationTask   != null) seedRotationTask.stop();
        if (databaseManager    != null) databaseManager.close();
        try {
            PacketEvents.getAPI().terminate();
        } catch (Exception ignored) { }
        getLogger().info("[AntiSeedCracker] Disabled.");
    }

    public void reload() {
        if (seedBroadcastTask != null) seedBroadcastTask.stop();
        if (seedRotationTask  != null) seedRotationTask.stop();
        unregisterListeners();

        reloadConfig();
        pluginConfig = new PluginConfig(getConfig());

        registerListeners();

        if (pluginConfig.isSeedRotationEnabled()) seedRotationTask.start();
        seedBroadcastTask.start();

        for (Player player : getServer().getOnlinePlayers()) {
            playerSessionListener.initPlayer(player);
        }

        getLogger().info("[AntiSeedCracker] Reloaded successfully.");
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        playerSessionListener     = new PlayerSessionListener(this);
        commandProtectionListener = new CommandProtectionListener(this);
        pm.registerEvents(playerSessionListener,     this);
        pm.registerEvents(commandProtectionListener, this);

        if (pluginConfig.isEyeOfEnderEnabled()) {
            eyeOfEnderProtector = new EyeOfEnderProtector(this);
            pm.registerEvents(eyeOfEnderProtector, this);
        }

        if (pluginConfig.isEndSpikesEnabled()) {
            endSpikeProtector = new EndSpikeProtector(this);
            pm.registerEvents(endSpikeProtector, this);
            getServer().getWorlds().forEach(endSpikeProtector::triggerModification);
        }

        if (pluginConfig.isEndCitiesEnabled()) {
            endCityProtector = new EndCityProtector(this);
            pm.registerEvents(endCityProtector, this);
        }
    }

    private void unregisterListeners() {
        if (playerSessionListener     != null) { playerSessionListener.unregister();     playerSessionListener     = null; }
        if (commandProtectionListener != null) { commandProtectionListener.unregister(); commandProtectionListener = null; }
        if (eyeOfEnderProtector       != null) { eyeOfEnderProtector.unregister();       eyeOfEnderProtector       = null; }
        if (endSpikeProtector         != null) { endSpikeProtector.unregister();         endSpikeProtector         = null; }
        if (endCityProtector          != null) { endCityProtector.unregister();          endCityProtector          = null; }
    }

    public PluginConfig        getPluginConfig()          { return pluginConfig; }
    public SeedManager         getSeedManager()           { return seedManager; }
    public WorldSeedInterceptor getWorldSeedInterceptor() { return worldSeedInterceptor; }
    public DatabaseManager     getDatabaseManager()       { return databaseManager; }
    public UpdateChecker       getUpdateChecker()         { return updateChecker; }
}
