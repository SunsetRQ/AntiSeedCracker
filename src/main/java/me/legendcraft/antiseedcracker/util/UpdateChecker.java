package me.legendcraft.antiseedcracker.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import me.legendcraft.antiseedcracker.AntiSeedCrackerPlugin;
import me.legendcraft.antiseedcracker.scheduler.FoliaSchedulerUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

public final class UpdateChecker {

    private static final String API_URL    = "https://api.modrinth.com/v2/project/%s/version";
    private static final String USER_AGENT = "AntiSeedCracker/%s (Modrinth; contact SunsetRQ7_)";
    private static final String UNSET_PLACEHOLDER = "YOUR_MODRINTH_PROJECT_ID";

    private final AntiSeedCrackerPlugin plugin;
    private final Logger                log;
    private final String                currentVersion;
    private final String                projectId;

    private volatile String  latestVersion = null;
    private volatile boolean upToDate      = true;

    public UpdateChecker(AntiSeedCrackerPlugin plugin, String projectId) {
        this.plugin         = plugin;
        this.log            = plugin.getLogger();
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.projectId      = projectId;
    }

    public void checkAsync() {
        if (projectId == null || projectId.isBlank()
                || projectId.equalsIgnoreCase(UNSET_PLACEHOLDER)) {
            log.info("[AntiSeedCracker] Update checker skipped — set "
                    + "'update_checker.modrinth_id' in config.yml after publishing.");
            return;
        }
        FoliaSchedulerUtil.runAsync(plugin, this::perform);
    }

    public String  getLatestVersion()  { return latestVersion; }
    public boolean isUpToDate()        { return upToDate; }
    public String  getCurrentVersion() { return currentVersion; }

    private void perform() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL, projectId)))
                    .header("User-Agent", String.format(USER_AGENT, currentVersion))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                log.warning("[AntiSeedCracker] Update checker: project '"
                        + projectId + "' not found on Modrinth. "
                        + "Verify 'update_checker.modrinth_id' in config.yml.");
                return;
            }
            if (response.statusCode() != 200) {
                log.fine("[AntiSeedCracker] Update checker: HTTP " + response.statusCode());
                return;
            }

            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            if (versions.isEmpty()) return;

            latestVersion = versions.get(0).getAsJsonObject()
                    .get("version_number").getAsString();

            if (latestVersion.equalsIgnoreCase(currentVersion)) {
                upToDate = true;
                log.info("[AntiSeedCracker] Running latest version (v" + currentVersion + ").");
            } else {
                upToDate = false;
                log.warning("╔════════════════════════════════════════════╗");
                log.warning("║   AntiSeedCracker — Update Available!      ║");
                log.warning("║   Current : v" + pad(currentVersion, 30) + "║");
                log.warning("║   Latest  : v" + pad(latestVersion,  30) + "║");
                log.warning("║   https://modrinth.com/plugin/"
                        + pad(projectId, 14) + "║");
                log.warning("╚════════════════════════════════════════════╝");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.fine("[AntiSeedCracker] Update check failed: " + e.getMessage());
        }
    }

    private static String pad(String s, int width) {
        if (s == null) s = "unknown";
        return s.length() >= width ? s.substring(0, width)
                                   : s + " ".repeat(width - s.length());
    }
}
