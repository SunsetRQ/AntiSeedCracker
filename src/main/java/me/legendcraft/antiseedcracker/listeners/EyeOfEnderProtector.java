package me.legendcraft.antiseedcracker.listeners;

import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import me.legendcraft.antiseedcracker.database.DatabaseManager;
import me.legendcraft.antiseedcracker.scheduler.FoliaSchedulerUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderSignal;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.security.SecureRandom;
import java.util.UUID;

public final class EyeOfEnderProtector implements Listener {

    private static final double EYE_JITTER_RADIANS = Math.toRadians(8.0);
    private static final SecureRandom RNG = new SecureRandom();

    private final AntiSeedCrackerPlugin plugin;

    public EyeOfEnderProtector(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEyeLaunch(ProjectileLaunchEvent event) {
        if (!plugin.getPluginConfig().isEyeOfEnderEnabled()) return;
        if (!(event.getEntity() instanceof EnderSignal eye)) return;

        World world = eye.getWorld();
        if (!plugin.getPluginConfig().getEyeOfEnderWorlds().contains(world.getName())) return;

        final org.bukkit.entity.Player shooterPlayer =
                (event.getEntity().getShooter() instanceof org.bukkit.entity.Player p) ? p : null;
        UUID shooterUUID = shooterPlayer != null ? shooterPlayer.getUniqueId() : null;

        final int[] fakeTarget;
        if (shooterUUID != null) {
            int[] stored = plugin.getSeedManager().getFakeStronghold(shooterUUID);
            if (stored != null) {
                fakeTarget = stored;
            } else {
                fakeTarget = plugin.getSeedManager().assignFakeStronghold(
                        (org.bukkit.entity.Player) event.getEntity().getShooter(),
                        plugin.getPluginConfig().getFakeStrongholdMinDist(),
                        plugin.getPluginConfig().getFakeStrongholdMaxDist()
                );
            }
        } else {
            int    min   = plugin.getPluginConfig().getFakeStrongholdMinDist();
            int    max   = plugin.getPluginConfig().getFakeStrongholdMaxDist();
            double angle = RNG.nextDouble() * 2.0 * Math.PI;
            int    dist  = min + RNG.nextInt(Math.max(1, max - min + 1));
            fakeTarget = new int[]{
                (int) Math.round(Math.cos(angle) * dist),
                (int) Math.round(Math.sin(angle) * dist)
            };
        }

        final int targetX = fakeTarget[0];
        final int targetZ = fakeTarget[1];

        try {
            Location eyeLoc     = eye.getLocation();
            double   dx         = targetX - eyeLoc.getX();
            double   dz         = targetZ - eyeLoc.getZ();
            double   distTarget = Math.sqrt(dx * dx + dz * dz);
            if (distTarget < 1.0) return;

            double speed = eye.getVelocity().length();
            if (speed < 0.01) speed = 0.3;

            double ndx = dx / distTarget;
            double ndz = dz / distTarget;

            double jitter = (RNG.nextDouble() * 2.0 - 1.0) * EYE_JITTER_RADIANS;
            double cos    = Math.cos(jitter);
            double sin    = Math.sin(jitter);

            double jx = ndx * cos - ndz * sin;
            double jz = ndx * sin + ndz * cos;

            eye.setVelocity(new org.bukkit.util.Vector(
                    jx * speed,
                    eye.getVelocity().getY(),
                    jz * speed
            ));

            DatabaseManager db = plugin.getDatabaseManager();
            if (db != null && plugin.getPluginConfig().isDatabaseLogEvents()
                    && shooterPlayer != null) {
                db.logEvent(DatabaseManager.EventType.EYE_REDIRECTED,
                        shooterUUID.toString(),
                        shooterPlayer.getName(),
                        eye.getWorld().getName(),
                        "Redirected to fake stronghold [" + targetX + ", " + targetZ + "]");
            }
        } catch (Exception e) {
            FoliaSchedulerUtil.runForEntity(plugin, eye, () -> {
                if (!eye.isValid()) return;
                try {
                    Location eyeLoc     = eye.getLocation();
                    double   dx         = targetX - eyeLoc.getX();
                    double   dz         = targetZ - eyeLoc.getZ();
                    double   distTarget = Math.sqrt(dx * dx + dz * dz);
                    if (distTarget < 1.0) return;
                    double speed = eye.getVelocity().length();
                    if (speed < 0.01) speed = 0.3;
                    double ndx = dx / distTarget;
                    double ndz = dz / distTarget;
                    double jitter = (RNG.nextDouble() * 2.0 - 1.0) * EYE_JITTER_RADIANS;
                    double cos = Math.cos(jitter);
                    double sin = Math.sin(jitter);
                    eye.setVelocity(new org.bukkit.util.Vector(
                            (ndx * cos - ndz * sin) * speed,
                            eye.getVelocity().getY(),
                            (ndx * sin + ndz * cos) * speed
                    ));
                } catch (Exception ignored) {}
            });
        }
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
