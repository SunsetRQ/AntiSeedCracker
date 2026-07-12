package me.legendcraft.antiseedcracker.seed;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SeedManager {

    private final ConcurrentHashMap<UUID, Long>   playerSeeds    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, long[]> fakeStrongholds = new ConcurrentHashMap<>();

    private final SecureRandom rng = new SecureRandom();

    public long assignSeed(Player player) {
        long seed = generateSeed();
        playerSeeds.put(player.getUniqueId(), seed);
        return seed;
    }

    public long getFakeSeed(Player player) {
        return playerSeeds.computeIfAbsent(player.getUniqueId(), k -> generateSeed());
    }

    public void removePlayer(UUID uuid) {
        playerSeeds.remove(uuid);
        fakeStrongholds.remove(uuid);
    }

    public int rotateAll() {
        int count = 0;
        for (UUID uuid : playerSeeds.keySet()) {
            playerSeeds.put(uuid, generateSeed());
            count++;
        }
        return count;
    }

    public void ensureAllPlayersHaveSeeds(Collection<? extends Player> players) {
        for (Player player : players) {
            playerSeeds.computeIfAbsent(player.getUniqueId(), k -> generateSeed());
        }
    }

    public int[] assignFakeStronghold(Player player, int minDist, int maxDist) {
        double angle  = rng.nextDouble() * 2.0 * Math.PI;
        int    dist   = minDist + rng.nextInt(Math.max(1, maxDist - minDist + 1));
        int    x      = (int) Math.round(Math.cos(angle) * dist);
        int    z      = (int) Math.round(Math.sin(angle) * dist);
        fakeStrongholds.put(player.getUniqueId(), new long[]{x, z});
        return new int[]{x, z};
    }

    public int[] getFakeStronghold(UUID uuid) {
        long[] packed = fakeStrongholds.get(uuid);
        if (packed == null) return null;
        return new int[]{(int) packed[0], (int) packed[1]};
    }

    public int trackedCount() {
        return playerSeeds.size();
    }

    private long generateSeed() {
        return rng.nextLong();
    }
}
