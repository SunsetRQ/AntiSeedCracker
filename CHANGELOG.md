# Changelog

All notable changes to AntiSeedCracker are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [3.5.0.2] - 2026-07-24

### Fixed
- **Buried treasure map locations were never actually scrambled**: `MapSeedInterceptor`
  filtered map decorations by name, checking for `"TREASURE"`, `"PILLAGER"`, and
  `"BASTION"` — none of which are real vanilla map decoration type IDs (verified against
  `org.bukkit.map.MapCursor.Type` directly: no such constants exist, so those three checks
  never matched anything, ever). Buried treasure maps actually use `TARGET_X`, which was
  missing entirely. Buried treasure is one of this plugin's own named SeedCrackerX
  structure signals, so its map marker was leaking the real, unscrambled location on every
  treasure map handed to a player. Now correctly matches `TARGET_X`; the two other
  never-matching checks were removed.
- **`end_cities`/`end_spikes` `modify_world: false` could permanently disable itself**:
  both `EndCityProtector` and `EndSpikeProtector` marked a chunk/world as "already
  processed" as soon as it was scanned, even when `modify_world` was off and nothing was
  actually changed. Turning `modify_world` on later via `/asc toggle` or a config edit
  would find that flag already set and skip the modification forever, for any chunk/world
  already loaded once while it was off. Both now only mark a chunk/world "done" when the
  modification actually ran (or, for End Cities, when a chunk provably has no End City at
  all and therefore never will need modifying).
- **Redundant respawn-cycle polling in `EndSpikeProtector`**: all four dragon-respawn End
  Crystals fire their placement event independently, each previously starting its own
  300-attempt polling loop to detect when the respawn animation finished — four times the
  necessary scheduled work per dragon fight. Now guarded so only one poll loop runs per
  world at a time.
- Replaced reflection-based scheduled-task cancellation (`getMethod("cancel").invoke(...)`,
  silently swallowing any failure) in `FoliaSchedulerUtil` with direct, typed
  `ScheduledTask#cancel()` calls — verified against the real Paper API class. Faster, and
  a genuine cancellation failure is no longer silently discarded.
- Removed a pointless seconds→ticks→seconds round-trip conversion in the seed rotation
  interval config.
- Simplified the End Spike pillar-angle formula to a plain decagon spacing expression;
  behaviorally identical (verified via the periodicity of `cos`/`sin`), just no longer
  needlessly obscure.
- Trimmed default `config.yml` down to essential settings, with fuller explanations kept
  in the README instead of long inline comments.

## [3.5.0.1] - 2026-07-22

### Fixed
- **Eye of Ender redirection didn't actually hold**: `EyeOfEnderProtector` only ever set the
  thrown eye's *velocity* toward the fake stronghold. Vanilla's `EnderSignal` entity is
  constructed with a target location pointing at the **real** stronghold before
  `ProjectileLaunchEvent` even fires, and re-accelerates toward that stored target every
  tick on its own — independent of whatever velocity a plugin sets once at launch. Without
  calling `EnderSignal#setTargetLocation`, the eye would curve back toward the true bearing
  within a second or two of flight, regardless of the initial jitter, leaking the real
  direction to anything tracking the eye's trajectory rather than just its launch frame.
  Now sets the target location (with a plausible, per-player-stable fake Y as well as
  x/z) in addition to velocity, on both the primary and Folia-retry code paths.
- **Fake stronghold silently moved on every `/asc reload`/`/asc toggle`**: `initPlayer()`
  unconditionally re-rolled each online player's fake stronghold every time it ran, which
  includes on every reload and every `/asc toggle` (which reloads internally) — not just on
  join. An admin flipping one unrelated toggle would make a previously-consistent `/locate
  stronghold` or Eye of Ender answer jump to a new location with no world change, undermining
  the exact consistency guarantee 3.5.0 added `getOrAssignFakeStructureLocation` to provide.
  Now only assigns a fake stronghold the first time a player is seen; already-assigned
  players keep theirs across reloads. `SeedManager` gained `hasFakeStronghold`/
  `getFakeStrongholdY` to support this.
- Verified with a clean `mvn clean package` on JDK 25 after both fixes.

## [3.5.0] - 2026-07-22

Live-tested end to end on a real Paper 26.1.2 server via RCON (repeated boots, every
`/asc` subcommand, both direct and `/execute`-wrapped bypass attempts, config
persistence across restarts). Several of the fixes below were only found because of
that live testing, not static review.

### Fixed
- **Build was broken as shipped**: `pom.xml` targeted Java 21 while the bundled
  `folia-api` 26.1.2 dependency ships Java 25 class files, so a clean checkout could
  not compile. Bumped to release 25 (JDK 25 confirmed installed and working).
- **`/execute`-wrapping command-blocker bypass**: vanilla's `/execute ... run <command>`
  re-dispatches its trailing command internally without re-firing
  `PlayerCommandPreprocessEvent`, so `/execute run seed` (or nested chains, even with a
  dimension switch like `/execute in minecraft:the_end run seed`) sailed straight past
  the blocker. Added `CommandUtil`, which recursively resolves what an `/execute` chain
  ultimately dispatches to before the block check runs. Verified live via RCON.
- **`/asc stats` bled into the next RCON command's response**: the command computed its
  result asynchronously and replied a tick later, after the RCON round-trip had already
  closed — the reply then attached itself to whatever command ran next over the same
  connection instead of `stats`. Found live via RCON testing. Now synchronous for
  console/RCON senders (async is kept for real players, to avoid blocking a tick on file
  I/O from chat).
- **Windows console mojibake**: em-dashes and box-drawing characters in
  `logger.info/warning()` calls rendered as `�` on a real Windows console (confirmed
  live). Chat-facing messages (Adventure `Component`) were unaffected and left as-is;
  only raw JUL logger output was converted to plain ASCII.
- **`/locate` answers were inconsistent on repeat queries**: non-stronghold structures
  got a brand-new random fake location on every call, letting a suspicious player detect
  the spoof just by re-running the command in place. Now cached per player+structure,
  same as strongholds already were.
- **Treasure/explorer map icons visibly jittered**: fake decoration coordinates were
  redrawn from fresh randomness on every `MAP_DATA` packet, so the icon jumped around
  the map on every refresh instead of sitting at one (wrong) spot. Now derived
  deterministically from map ID + icon type + real position, so it's stable but still
  unpredictable to the player.
- **Unbounded cache growth in `StructureReconMonitor`**: the per-chunk "does this chunk
  have a tracked structure" cache never evicted, growing for the entire server uptime.
  Now evicted on `ChunkUnloadEvent`, bounding it to currently-loaded chunks.
- **`extra_blocked_commands` namespace-stripping bug**: the old colon-stripping logic
  stripped from the *first* colon anywhere in the string, not just a leading
  `namespace:` prefix, so a command with a namespaced argument later in the string
  could have its root command mangled. Fixed in the new shared `CommandUtil`.
- Version strings were inconsistent across `pom.xml` (3.4.0, itself unreleased/broken),
  `plugin.yml` (3.3.1), and `README.md` (3.3.0); `plugin.yml`'s description still
  mentioned "plugin-API interception," a feature removed back in 3.3.0. All unified.

### Added
- `/asc toggle <feature> [true|false]` — flip or explicitly set one protection layer
  live, persisted to `config.yml`, no full reload needed. Tab-completes feature names.
- Granular permissions: `antiseedcracker.admin.status` (read-only) and
  `antiseedcracker.admin.manage` (reload/toggle), both covered by the existing
  `antiseedcracker.admin` umbrella so nothing breaks for existing setups.
- `/asc help` — lists only the subcommands the sender actually has permission for.
- **Command Probing Monitor**: logs (never blocks, same philosophy as the structure
  recon monitor) when a player triggers several blocked seed commands in a short
  window — a pattern consistent with a script/macro probing for a bypass.
- `structure_recon_monitor.tracked_structures` is now configurable; defaults now
  include `buried_treasure`, which the plugin's own threat-model docs already named as
  a SeedCrackerX data source but which was missing from the actual tracked set.

### Researched
- Surveyed SeedCrackerX's current (1.18+) technique set, LiquidBounce/Wurst/Meteor
  Client's public source and issue trackers (no seed-cracking integration or
  spoof-detection features found in any of them — ESP/X-ray modules only render chunk
  data already sent to the client), and competing plugins (`akshualy/AntiSeedCracker`,
  `zAntiCracker`, `DrexHD/SeedGuard`, `Earthcomputer/SecureSeed`). Findings folded into
  the README's Threat Model section, including an honest note that hashed-seed
  spoofing/rotation is a "raises the bar" layer, not a load-bearing one, against a
  well-informed attacker relying on structure-position data instead.
- Documented, as a known limitation, that command-block- and data-pack-`/function`-
  issued commands never fire any Bukkit command-preprocess event at all — there is no
  supported API hook for either, so this can't be closed without NMS-level hacks the
  project deliberately avoids.

## [3.3.1] - 2026-07-12

- `pluginConfig`/`databaseManager` were plain fields reassigned by `/asc reload` on the
  main thread but read from PacketEvents Netty threads and the async scheduler with no
  happens-before edge; marked `volatile`.
- `extra_blocked_commands` was never lowercased at load, so any mixed-case entry in
  `config.yml` silently failed to match.
- `StructureReconMonitor` called `chunk.getStructures()` on every chunk crossing for
  every player with no cross-player caching; added a per-world chunk-level cache so
  each chunk's lookup happens once total. Added `ANCIENT_CITY` to the tracked set.
- `DatabaseManager` opened and closed a file handle per audit event; now keeps a
  persistent writer, rotated only on date change.
- Removed dead code left over from the removed `WorldSeedInterceptor`.
- Verified live on Paper 26.1.2-74 and Folia 26.1.2-8, zero errors, real seed confirmed
  byte-for-byte intact on disk.

## [3.3.0] - 2026-07-11

- **Critical fix**: removed `WorldSeedInterceptor`, which patched the live NMS seed
  field once per second on every loaded world. That field is re-read fresh by vanilla
  chunk decoration (trees, ores, structure placement) for every chunk generated, not
  cached once at world load — any chunk decorated while it held a fake value generated
  from randomness instead of the real seed. This was the root cause of reported "seed
  changing disrupts chunk generation" issues.
- Replaced it with `SeedIntegrityMonitor`, a read-only watchdog that only ever reads
  `world.getSeed()` and warns on drift, never writes to it.
- `EndCityProtector` now scans only the structure's bounding box instead of the full
  chunk column.
- Player-seed safety-net task interval reduced from 1s to 15s.
- Researched LiquidBounce/SeedCrackerX mechanics and documented honestly that seed
  cracking via legitimately-visited structure coordinates is not a network exploit and
  cannot be prevented server-side.
- Added `StructureReconMonitor` (audit-log only, never blocks) and
  `SlimeChunkObfuscator` (opt-in, off by default).
- Documented that block/ore ESP (XRay, StorageESP) is out of scope — use Paper's
  native anti-xray instead.

## [3.2.0] - 2026-07-04

- Added bStats (plugin id 32378) with platform/protection charts.
- Upgraded PacketEvents 2.12.2 → 2.13.0.
- Configurable seed-blocked messages via `config.yml`.
- `/asc reload` now applies database enable/disable without a restart.
- Verified live: Paper 26.1.2-72 and Folia 26.1.2-8 boot with zero errors.

## [3.1.1] - 2026-06-13

- Fixed `NoClassDefFoundError: net/kyori/adventure/nbt/BinaryTag` on Folia/Paper by
  bundling `adventure-nbt` in the shaded jar (PacketEvents declared it as a compile
  dependency without bundling it).
- Upgraded PacketEvents 2.12.0 → 2.12.2.

## [3.1.0] - 2026-06-13

- Initial release: professional multi-layer world seed protection for Paper, Purpur,
  and Folia.
