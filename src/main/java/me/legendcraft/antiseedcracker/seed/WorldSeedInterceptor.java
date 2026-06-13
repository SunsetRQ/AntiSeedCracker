package me.legendcraft.antiseedcracker.seed;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorldSeedInterceptor {

    private Method getHandleMethod;
    private List<Field> containerPath = Collections.emptyList();
    private Field seedField;
    private final boolean available;
    private final Logger logger;

    private static final int MAX_DEPTH  = 7;
    private static final int MAX_VISITS = 10_000;
    private static final String[] TRAVERSE_PREFIXES = { "net.minecraft.", "com.mojang." };
    private static final long SENTINEL = 0xDEAD_C0DE_1337_4242L;

    public WorldSeedInterceptor(Logger logger) {
        this.logger = logger;
        boolean ok = false;
        try {
            ok = init();
        } catch (Exception e) {
            logger.warning("[AntiSeedCracker] WorldSeedInterceptor init error: " + e);
        }
        this.available = ok;
    }

    public boolean isAvailable() {
        return available;
    }

    public void patchWorld(World world, long fakeSeed) {
        if (!available) return;
        try {
            Object container = getHandleMethod.invoke(world);
            for (Field f : containerPath) {
                container = f.get(container);
                if (container == null) return;
            }
            seedField.setLong(container, fakeSeed);
        } catch (Exception e) {
            logger.log(Level.FINE, "[AntiSeedCracker] WorldSeedInterceptor.patchWorld failed", e);
        }
    }

    private boolean init() throws Exception {
        Class<?> craftWorldClass = resolveCraftWorldClass();
        getHandleMethod = craftWorldClass.getDeclaredMethod("getHandle");
        getHandleMethod.setAccessible(true);

        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            logger.warning("[AntiSeedCracker] WorldSeedInterceptor: no worlds loaded yet — disabling.");
            return false;
        }
        World  refWorld = worlds.get(0);
        Object nmsLevel = getHandleMethod.invoke(refWorld);

        List<SeedPath> candidates = new ArrayList<>();
        collectDirectCandidates(nmsLevel, candidates);
        bfsFindAll(nmsLevel, candidates);

        logger.info("[AntiSeedCracker] WorldSeedInterceptor: found "
                + candidates.size() + " long 'seed' candidate(s) — verifying each...");

        for (SeedPath path : candidates) {
            long orig = Long.MIN_VALUE;
            boolean origRead = false;
            try {
                path.seedField().setAccessible(true);
                orig = path.seedField().getLong(path.container());
                origRead = true;

                path.seedField().setLong(path.container(), SENTINEL);
                boolean verified;
                try {
                    verified = (refWorld.getSeed() == SENTINEL);
                } finally {
                    path.seedField().setLong(path.container(), orig);
                }

                if (verified) {
                    containerPath = path.containerPath();
                    seedField     = path.seedField();
                    for (Field f : containerPath) f.setAccessible(true);

                    logger.info("[AntiSeedCracker] WorldSeedInterceptor: ✔ active — "
                            + "world.getSeed() intercepted. Class: "
                            + path.seedField().getDeclaringClass().getSimpleName()
                            + ", field: " + path.seedField().getName()
                            + ", depth: " + containerPath.size());
                    return true;
                } else {
                    logger.fine("[AntiSeedCracker] WorldSeedInterceptor: candidate "
                            + path.seedField().getDeclaringClass().getSimpleName() + "."
                            + path.seedField().getName() + " did not affect getSeed() — skipping.");
                }
            } catch (Exception e) {
                if (origRead) {
                    try { path.seedField().setLong(path.container(), orig); } catch (Exception ignored) {}
                }
                logger.fine("[AntiSeedCracker] WorldSeedInterceptor: candidate threw "
                        + e.getClass().getSimpleName() + " — skipping.");
            }
        }

        logger.warning("[AntiSeedCracker] WorldSeedInterceptor: none of the "
                + candidates.size() + " candidate(s) controlled world.getSeed(). "
                + "Plugin-API seed protection is disabled; packet-level protection remains active.");
        return false;
    }

    private static void collectDirectCandidates(Object obj, List<SeedPath> out) {
        for (Field f : allDeclaredFields(obj.getClass())) {
            if (f.getType() == long.class && "seed".equalsIgnoreCase(f.getName())) {
                try {
                    f.setAccessible(true);
                    out.add(new SeedPath(Collections.emptyList(), f, obj));
                } catch (Exception ignored) { }
            }
        }
    }

    private static void bfsFindAll(Object root, List<SeedPath> out) {
        record Node(Object obj, List<Field> path) {}

        Set<Integer>     visited = new HashSet<>();
        ArrayDeque<Node> queue   = new ArrayDeque<>();
        queue.add(new Node(root, Collections.emptyList()));
        int visits = 0;

        while (!queue.isEmpty() && visits < MAX_VISITS) {
            Node node = queue.poll();
            if (node.obj() == null) continue;
            int id = System.identityHashCode(node.obj());
            if (!visited.add(id)) continue;
            visits++;

            for (Field f : allDeclaredFields(node.obj().getClass())) {
                try {
                    f.setAccessible(true);

                    if (f.getType() == long.class && "seed".equalsIgnoreCase(f.getName())) {
                        out.add(new SeedPath(new ArrayList<>(node.path()), f, node.obj()));
                        continue;
                    }

                    if (node.path().size() < MAX_DEPTH
                            && !f.getType().isPrimitive()
                            && !f.getType().isArray()
                            && !f.getType().isEnum()
                            && isTraversable(f.getType())) {
                        Object nested = f.get(node.obj());
                        if (nested != null && nested != node.obj()) {
                            List<Field> np = new ArrayList<>(node.path());
                            np.add(f);
                            queue.add(new Node(nested, np));
                        }
                    }
                } catch (Exception ignored) { }
            }
        }
    }

    private static boolean isTraversable(Class<?> cls) {
        String name = cls.getName();
        for (String prefix : TRAVERSE_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private static List<Field> allDeclaredFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) result.add(f);
            c = c.getSuperclass();
        }
        return result;
    }

    private static Class<?> resolveCraftWorldClass() throws ClassNotFoundException {
        try {
            return Class.forName("org.bukkit.craftbukkit.CraftWorld");
        } catch (ClassNotFoundException e) {
            String ver = detectVersion();
            return Class.forName("org.bukkit.craftbukkit." + ver + ".CraftWorld");
        }
    }

    private static String detectVersion() {
        try {
            String[] parts = Bukkit.getServer().getClass().getName().split("\\.");
            if (parts.length >= 5) return parts[3];
        } catch (Exception ignored) { }
        return "v1_21_R3";
    }

    private record SeedPath(List<Field> containerPath, Field seedField, Object container) { }
}
