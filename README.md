# AntiSeedCracker

[![Build](https://github.com/SunsetRQ/AntiSeedCracker/actions/workflows/build.yml/badge.svg)](https://github.com/SunsetRQ/AntiSeedCracker/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/SunsetRQ/AntiSeedCracker)](https://github.com/SunsetRQ/AntiSeedCracker/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)

**Version:** 3.2.0 &nbsp;|&nbsp; **Target:** Minecraft 26.1.2 &nbsp;|&nbsp; **Author:** SunsetRQ7_

AntiSeedCracker is a professional, multi-layer world seed protection plugin for Paper, Purpur, and Folia. It blocks every known technique a player can use to recover your world seed, protecting your server from ESP clients, ghost clients, and seed-cracking tools such as SeedCrackerX.

---

## Features

| Protection Layer | Description |
|---|---|
| **Login & Respawn Packet Spoofing** | Replaces the hashed seed in `JOIN_GAME` and `RESPAWN` network packets with a per-player cryptographically random fake seed. Operates at the Netty I/O level via PacketEvents before any client ever receives the real value. |
| **Dynamic Seed Rotation** | Periodically assigns each online player a new random fake seed (configurable interval, minimum 30 s). Defeats crackers that accumulate packets across multiple sessions. |
| **Plugin API Protection** | Patches the NMS seed field via reflection every second so any plugin calling `World.getSeed()` receives a rotating fake value. Re-applies unconditionally to prevent other plugins from undoing the patch. |
| **Eye of Ender Redirection** | Intercepts thrown Eyes of Ender and redirects their velocity toward a per-player fake stronghold location. Applies a ±8° angular jitter to prevent exact triangulation across repeated throws. |
| **/locate Spoofing** | Cancels `/locate` and replies with convincingly fake coordinates. Stronghold queries use the player's stable fake location for consistency. |
| **/seed Command Block** | Blocks `/seed`, `/minecraft:seed`, `/getseed`, `/worldseed`, and any extra commands configured in `config.yml` for players, console, and RCON. Also strips seed commands from tab-complete results. |
| **End Spike Randomization** | Shuffles the bedrock cap heights of all 10 End obsidian pillars at world load and after each dragon respawn cycle. Breaks the vanilla height fingerprint `{76, 79, 82 … 103}` that uniquely identifies the seed. |
| **End City Glass Replacement** | Replaces magenta stained glass in End City chunks with purpur blocks on first load. Prevents End City coordinate fingerprinting via the distinctive glass pattern. |
| **Treasure & Explorer Map Scrambling** | Intercepts `MAP_DATA` packets and randomises the on-map position of every structure icon (buried treasure, woodland mansion, ocean monument, and others). |
| **Audit Log** | Appends every blocked command, spoofed packet, and redirected Eye to a flat-file TSV log in `plugins/AntiSeedCracker/logs/`. Zero external dependencies — no SQLite. |

---

## Compatibility

| Platform | Supported | Verified |
|---|---|---|
| Paper 26.1.2 | ✔ | ✔ Live-tested on build 72, zero errors |
| Folia 26.1.2 | ✔ (region-threaded scheduling) | ✔ Live-tested on build 8, zero errors |
| Purpur 26.1.2 | ✔ (Paper-compatible) | — |
| Spigot / CraftBukkit | ✘ Not supported — the plugin uses Paper-only APIs (Adventure, AsyncScheduler) | — |

**Runtime:** Java 25 or higher is required.

## Metrics

Anonymous usage statistics are collected via [bStats](https://bstats.org/plugin/bukkit/AntiSeedCracker/32378). This can be disabled globally in `plugins/bStats/config.yml`.

---

## Installation

1. Download `AntiSeedCracker-3.2.0.jar` from [Releases](../../releases/latest).
2. Place it in your server's `plugins/` folder.
3. Start or restart the server.
4. Edit `plugins/AntiSeedCracker/config.yml` to your preference.
5. Run `/asc reload` to apply changes without restarting.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/asc reload` | Reloads config and restarts all protection tasks | `antiseedcracker.admin` |
| `/asc status` | Shows which protection layers are active | `antiseedcracker.admin` |
| `/asc stats` | Displays aggregate event counts from the audit log | `antiseedcracker.admin` |
| `/asc info` | Shows version, author, platform, and update status | `antiseedcracker.admin` |

The `antiseedcracker.admin` permission defaults to server operators.

---

## Configuration

```yaml
seed_obfuscation:
  intercept_login: true
  intercept_respawn: true

seed_rotation:
  enabled: true
  interval_seconds: 60

structure_protection:
  end_spikes:
    enabled: true
    worlds:
      - world_the_end
    modify_world: true
  end_cities:
    enabled: true
    worlds:
      - world_the_end
    modify_world: true
  spoof_locate_command:
    enabled: true
    max_offset: 2000

plugin_api_protection:
  enabled: true

eye_of_ender_protection:
  enabled: true
  worlds:
    - world
  fake_stronghold_min_distance: 800
  fake_stronghold_max_distance: 4000

update_checker:
  enabled: true
  modrinth_id: "YOUR_MODRINTH_PROJECT_ID"

database:
  enabled: true
  log_events: true
  max_event_age_days: 30

treasure_map_protection:
  enabled: true

messages:
  seed_blocked_player: "&c[AntiSeedCracker] Access to world seed information is restricted."
  seed_blocked_console: "&c[AntiSeedCracker] Seed access is restricted. The real seed is never exposed."

extra_blocked_commands:
  - "seedcracker"
  - "seedfind"
  - "worldseedinfo"
```

### `modify_world` Warning

Setting `modify_world: true` for End spikes or End cities **permanently changes world data**. Spike bedrock caps are moved to shuffled positions and End City magenta glass is replaced with purpur blocks. These changes persist across restarts. Set to `false` to disable physical world modifications while keeping all other protection layers active.

---

## World Safety

AntiSeedCracker never reads, stores, or logs the real world seed. The only world modifications performed are:

- Moving bedrock cap blocks on End obsidian pillars (when `end_spikes.modify_world: true`).
- Replacing magenta stained glass in End City chunks with purpur blocks (when `end_cities.modify_world: true`).

Both modifications use `physicsUpdate: false` to prevent block-update cascades. Each chunk is tagged with a Persistent Data Container key so the modification runs **at most once per chunk** across all server restarts. No terrain, biome, or structure data outside these specific block types is ever altered.

---

## Folia Threading Model

All block mutations are dispatched to the region thread owning the relevant chunk via `RegionScheduler` (Folia) or the main thread (Paper/Spigot). Packet interceptors run on the Netty I/O thread and only perform `ConcurrentHashMap` lookups. The seed rotation and world-seed broadcast tasks run on the `AsyncScheduler` (Paper/Folia) or an asynchronous `BukkitTask` (Spigot).

---

## Known Bypass Mitigations

| Attack Vector | Mitigation |
|---|---|
| Login/Respawn packet interception | PacketEvents HIGHEST-priority intercept |
| `/seed`, `/getseed`, `/worldseed` | `PlayerCommandPreprocessEvent` + `ServerCommandEvent` + `RemoteServerCommandEvent` blocking |
| Eye of Ender triangulation | Per-player fake stronghold + ±8° jitter |
| `/locate structure` triangulation | Spoofed coordinate output, consistent per-player stronghold fake |
| Treasure / explorer maps | `MAP_DATA` packet decoration scrambling |
| End spike height fingerprinting | Full Fisher-Yates shuffle of pillar cap heights |
| End City coordinate fingerprinting | Magenta glass block replacement |
| Other plugin API (`world.getSeed()`) | NMS field reflection patch, re-applied unconditionally every second |
| Tab-complete exposure | `TabCompleteEvent` filtering |
| RCON seed commands | `RemoteServerCommandEvent` blocking |

---

## Building from Source

Requires Maven 3.6+ and **JDK 25**. The project compiles against folia-api 26.1.2 (Java 25 class files) and produces a Java 25 output jar.

```sh
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-25.0.3
mvn clean package

# Linux / macOS
export JAVA_HOME=/path/to/jdk-25
mvn clean package
```

The shaded jar is produced at `target/AntiSeedCracker-3.2.0.jar`. PacketEvents 2.13.0 and bStats 3.2.1 are bundled and relocated under `me.legendcraft.antiseedcracker.lib`.

---

## Design Principles

- **Never punish players.** AntiSeedCracker never kicks or bans anyone — it silently feeds cheat clients useless data.
- **Never touch the real seed.** The plugin never reads, stores, transmits, or logs the actual world seed.
- **Never corrupt the world.** All optional world modifications are single-block, physics-free, idempotent, and clearly documented.
- **Fully asynchronous.** Packet work happens on Netty I/O threads; periodic tasks run on async schedulers; block mutations are dispatched to the owning region thread on Folia.

---

## License

Released under the [MIT License](LICENSE). Free to use, modify, and redistribute.
