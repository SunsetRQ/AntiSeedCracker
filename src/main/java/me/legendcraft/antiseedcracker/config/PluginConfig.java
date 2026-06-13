package me.legendcraft.antiseedcracker.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;

public final class PluginConfig {

    private final boolean interceptLogin;
    private final boolean interceptRespawn;

    private final boolean seedRotationEnabled;
    private final long    seedRotationIntervalTicks;

    private final boolean      endSpikesEnabled;
    private final List<String> endSpikeWorlds;
    private final boolean      endSpikesModifyWorld;

    private final boolean      endCitiesEnabled;
    private final List<String> endCityWorlds;
    private final boolean      endCitiesModifyWorld;

    private final boolean spoofLocateEnabled;
    private final int     spoofLocateMaxOffset;

    private final boolean pluginApiProtectionEnabled;

    private final boolean      eyeOfEnderEnabled;
    private final List<String> eyeOfEnderWorlds;
    private final int          fakeStrongholdMinDist;
    private final int          fakeStrongholdMaxDist;

    private final boolean updateCheckerEnabled;
    private final String  modrinthProjectId;

    private final boolean databaseEnabled;
    private final boolean databaseLogEvents;
    private final int     databaseMaxEventAgeDays;

    private final boolean treasureMapProtectionEnabled;

    private final List<String> extraBlockedCommands;

    public PluginConfig(FileConfiguration cfg) {
        this.interceptLogin   = cfg.getBoolean("seed_obfuscation.intercept_login",   true);
        this.interceptRespawn = cfg.getBoolean("seed_obfuscation.intercept_respawn", true);

        this.seedRotationEnabled = cfg.getBoolean("seed_rotation.enabled", true);
        long rawSecs = Math.max(30L, cfg.getLong("seed_rotation.interval_seconds", 60L));
        this.seedRotationIntervalTicks = rawSecs * 20L;

        this.endSpikesEnabled     = cfg.getBoolean("structure_protection.end_spikes.enabled",      true);
        this.endSpikeWorlds       = Collections.unmodifiableList(
                cfg.getStringList("structure_protection.end_spikes.worlds"));
        this.endSpikesModifyWorld = cfg.getBoolean("structure_protection.end_spikes.modify_world", true);

        this.endCitiesEnabled     = cfg.getBoolean("structure_protection.end_cities.enabled",      true);
        this.endCityWorlds        = Collections.unmodifiableList(
                cfg.getStringList("structure_protection.end_cities.worlds"));
        this.endCitiesModifyWorld = cfg.getBoolean("structure_protection.end_cities.modify_world", true);

        this.spoofLocateEnabled   = cfg.getBoolean("structure_protection.spoof_locate_command.enabled",    true);
        this.spoofLocateMaxOffset = Math.max(100,
                cfg.getInt("structure_protection.spoof_locate_command.max_offset", 2000));

        this.pluginApiProtectionEnabled = cfg.getBoolean("plugin_api_protection.enabled", true);

        this.eyeOfEnderEnabled     = cfg.getBoolean("eye_of_ender_protection.enabled", true);
        this.eyeOfEnderWorlds      = Collections.unmodifiableList(
                cfg.getStringList("eye_of_ender_protection.worlds"));
        this.fakeStrongholdMinDist = Math.max(100,
                cfg.getInt("eye_of_ender_protection.fake_stronghold_min_distance", 800));
        this.fakeStrongholdMaxDist = Math.max(this.fakeStrongholdMinDist + 100,
                cfg.getInt("eye_of_ender_protection.fake_stronghold_max_distance", 4000));

        this.updateCheckerEnabled = cfg.getBoolean("update_checker.enabled", true);
        this.modrinthProjectId    = cfg.getString("update_checker.modrinth_id",
                "YOUR_MODRINTH_PROJECT_ID");

        this.databaseEnabled        = cfg.getBoolean("database.enabled", true);
        this.databaseLogEvents      = cfg.getBoolean("database.log_events", true);
        this.databaseMaxEventAgeDays = Math.max(1,
                cfg.getInt("database.max_event_age_days", 30));

        this.treasureMapProtectionEnabled =
                cfg.getBoolean("treasure_map_protection.enabled", true);

        this.extraBlockedCommands = Collections.unmodifiableList(
                cfg.getStringList("extra_blocked_commands"));
    }

    public boolean isInterceptLogin()             { return interceptLogin; }
    public boolean isInterceptRespawn()           { return interceptRespawn; }

    public boolean isSeedRotationEnabled()        { return seedRotationEnabled; }
    public long    getSeedRotationIntervalTicks() { return seedRotationIntervalTicks; }

    public boolean      isEndSpikesEnabled()     { return endSpikesEnabled; }
    public List<String> getEndSpikeWorlds()      { return endSpikeWorlds; }
    public boolean      isEndSpikesModifyWorld() { return endSpikesModifyWorld; }

    public boolean      isEndCitiesEnabled()     { return endCitiesEnabled; }
    public List<String> getEndCityWorlds()       { return endCityWorlds; }
    public boolean      isEndCitiesModifyWorld() { return endCitiesModifyWorld; }

    public boolean isSpoofLocateEnabled()        { return spoofLocateEnabled; }
    public int     getSpoofLocateMaxOffset()     { return spoofLocateMaxOffset; }

    public boolean isPluginApiProtectionEnabled() { return pluginApiProtectionEnabled; }

    public boolean      isEyeOfEnderEnabled()      { return eyeOfEnderEnabled; }
    public List<String> getEyeOfEnderWorlds()      { return eyeOfEnderWorlds; }
    public int          getFakeStrongholdMinDist() { return fakeStrongholdMinDist; }
    public int          getFakeStrongholdMaxDist() { return fakeStrongholdMaxDist; }

    public boolean isUpdateCheckerEnabled()    { return updateCheckerEnabled; }
    public String  getModrinthProjectId()      { return modrinthProjectId; }

    public boolean isDatabaseEnabled()         { return databaseEnabled; }
    public boolean isDatabaseLogEvents()       { return databaseLogEvents; }
    public int     getDatabaseMaxEventAgeDays(){ return databaseMaxEventAgeDays; }

    public boolean isTreasureMapProtectionEnabled() { return treasureMapProtectionEnabled; }

    public List<String> getExtraBlockedCommands()   { return extraBlockedCommands; }
}
