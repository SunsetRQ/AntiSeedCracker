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
import me.legendcraft.antiseedcracker.listeners.SlimeChunkObfuscator;
import me.legendcraft.antiseedcracker.listeners.StructureReconMonitor;
import me.legendcraft.antiseedcracker.packets.LoginPacketInterceptor;
import me.legendcraft.antiseedcracker.packets.MapSeedInterceptor;
import me.legendcraft.antiseedcracker.packets.RespawnPacketInterceptor;
import me.legendcraft.antiseedcracker.scheduler.FoliaSchedulerUtil;
import me.legendcraft.antiseedcracker.seed.SeedIntegrityMonitor;
import me.legendcraft.antiseedcracker.seed.SeedManager;
import me.legendcraft.antiseedcracker.tasks.SeedBroadcastTask;
import me.legendcraft.antiseedcracker.tasks.SeedRotationTask;
import me.legendcraft.antiseedcracker.util.PlatformUtil;
import me.legendcraft.antiseedcracker.util.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class AntiSeedCrackerPlugin extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 32378;

    private volatile PluginConfig    pluginConfig;
    private volatile DatabaseManager databaseManager;
    private SeedManager     seedManager;
    private UpdateChecker   updateChecker;

    private SeedBroadcastTask seedBroadcastTask;
    private SeedRotationTask  seedRotationTask;

    private SeedIntegrityMonitor seedIntegrityMonitor;

    private PlayerSessionListener     playerSessionListener;
    private CommandProtectionListener commandProtectionListener;
    private EyeOfEnderProtector       eyeOfEnderProtector;
    private EndSpikeProtector         endSpikeProtector;
    private EndCityProtector          endCityProtector;
    private SlimeChunkObfuscator      slimeChunkObfuscator;
    private StructureReconMonitor     structureReconMonitor;

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

        seedManager = new SeedManager();

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

        seedIntegrityMonitor = new SeedIntegrityMonitor(getLogger());

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

        setupMetrics();

        getLogger().info("[AntiSeedCracker] Enabled — real seed is protected.");
    }

    private void setupMetrics() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("platform_type", PlatformUtil::name));
        metrics.addCustomChart(new SimplePie("seed_integrity_monitor_enabled",
                () -> pluginConfig.isSeedIntegrityMonitorEnabled() ? "enabled" : "disabled"));
        metrics.addCustomChart(new SimplePie("seed_rotation_enabled",
                () -> pluginConfig.isSeedRotationEnabled() ? "enabled" : "disabled"));
        metrics.addCustomChart(new SimplePie("audit_log_enabled",
                () -> databaseManager != null ? "enabled" : "disabled"));
        metrics.addCustomChart(new SimplePie("slime_chunk_obfuscation_enabled",
                () -> pluginConfig.isSlimeChunkObfuscationEnabled() ? "enabled" : "disabled"));
        metrics.addCustomChart(new SimplePie("structure_recon_monitor_enabled",
                () -> pluginConfig.isStructureReconMonitorEnabled() ? "enabled" : "disabled"));
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

        if (pluginConfig.isDatabaseEnabled() && databaseManager == null) {
            try {
                databaseManager = new DatabaseManager(getDataFolder(), getLogger());
                databaseManager.init();
                final int maxDays = pluginConfig.getDatabaseMaxEventAgeDays();
                FoliaSchedulerUtil.runAsync(this, () -> databaseManager.pruneOldEvents(maxDays));
            } catch (Exception e) {
                getLogger().warning("[AntiSeedCracker] Audit log init failed on reload: "
                        + e.getMessage());
                databaseManager = null;
            }
        } else if (!pluginConfig.isDatabaseEnabled() && databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }

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

        if (pluginConfig.isSlimeChunkObfuscationEnabled()) {
            slimeChunkObfuscator = new SlimeChunkObfuscator(this);
            pm.registerEvents(slimeChunkObfuscator, this);
        }

        if (pluginConfig.isStructureReconMonitorEnabled()) {
            structureReconMonitor = new StructureReconMonitor(this);
            pm.registerEvents(structureReconMonitor, this);
        }
    }

    private void unregisterListeners() {
        if (playerSessionListener     != null) { playerSessionListener.unregister();     playerSessionListener     = null; }
        if (commandProtectionListener != null) { commandProtectionListener.unregister(); commandProtectionListener = null; }
        if (eyeOfEnderProtector       != null) { eyeOfEnderProtector.unregister();       eyeOfEnderProtector       = null; }
        if (endSpikeProtector         != null) { endSpikeProtector.unregister();         endSpikeProtector         = null; }
        if (endCityProtector          != null) { endCityProtector.unregister();          endCityProtector          = null; }
        if (slimeChunkObfuscator      != null) { slimeChunkObfuscator.unregister();      slimeChunkObfuscator      = null; }
        if (structureReconMonitor     != null) { structureReconMonitor.unregister();     structureReconMonitor     = null; }
    }

    public PluginConfig          getPluginConfig()          { return pluginConfig; }
    public SeedManager           getSeedManager()           { return seedManager; }
    public SeedIntegrityMonitor  getSeedIntegrityMonitor()  { return seedIntegrityMonitor; }
    public StructureReconMonitor getStructureReconMonitor() { return structureReconMonitor; }
    public DatabaseManager       getDatabaseManager()       { return databaseManager; }
    public UpdateChecker         getUpdateChecker()         { return updateChecker; }
}
