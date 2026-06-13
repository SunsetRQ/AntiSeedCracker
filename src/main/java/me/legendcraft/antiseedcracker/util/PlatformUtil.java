package me.legendcraft.antiseedcracker.util;

public final class PlatformUtil {

    public static final boolean IS_FOLIA;
    public static final boolean IS_PURPUR;
    public static final boolean IS_PAPER;

    static {
        IS_FOLIA  = classExists("io.papermc.paper.threadedregions.RegionizedServer");
        IS_PURPUR = classExists("net.pl3x.purpur.PurpurConfig")
                 || classExists("org.purpurmc.purpur.PurpurConfig");
        IS_PAPER  = IS_FOLIA
                 || IS_PURPUR
                 || classExists("io.papermc.paper.ServerBuildInfo")
                 || classExists("io.papermc.paper.configuration.GlobalConfiguration")
                 || classExists("com.destroystokyo.paper.PaperConfig");
    }

    private PlatformUtil() {}

    public static String name() {
        if (IS_FOLIA)  return "Folia";
        if (IS_PURPUR) return "Purpur";
        if (IS_PAPER)  return "Paper";
        return "Spigot/CraftBukkit";
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
