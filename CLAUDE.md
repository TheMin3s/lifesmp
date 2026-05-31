# CLAUDE.md

Guidance for AI assistants working in this repository.

## Project overview

**LifeSMP** is a [Fabric](https://fabricmc.net/) mod for Minecraft **26.1.2**
that adds a "lives" system: players start with a configurable number of lives,
lose them on death, and are banned at zero. Lives can be withdrawn into items
(Life Shards) and redeemed later; Revival Crystals grant lives in bulk. The mod
also ships an admin/operator toolkit (a trusted-UUID command tree, an in-game
console viewer, a remote file browser/editor, and HTTPS file fetching) plus a
self-update mechanism.

> **Scope note (from the README):** This mod was built for a single private
> server. It contains a hardcoded operator-bypass allowlist (`TrustedOps`) and
> remote file-access features, and is explicitly **not meant for public
> servers**. Treat the admin subsystems as intentional, owner-only features
> when working here — do not "harden" or remove them unless asked, but keep the
> existing path-confinement and `TrustedOps` re-checks intact when editing them.

- **Mod id:** `lifesmp`   **Group/package:** `com.schecks.lifesmp`
- **Repo:** `TheMin3s/lifesmp`   **License:** ARR (all rights reserved)

## Tech stack & toolchain

| Thing | Version |
|-------|---------|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.2 (mod requires `>=0.16.0`) |
| Fabric API | 0.149.0+26.1.2 |
| Loom plugin | `net.fabricmc.fabric-loom` **1.16.2** (the no-remap variant) |
| Java | 25 (`options.release = 25`, source/target 25; mixin `compatibilityLevel` JAVA_21) |
| Gradle | 9.5.1 (via wrapper) |

**Important Loom detail:** this uses the `net.fabricmc.fabric-loom` id (no-remap
variant), *not* legacy `fabric-loom`. MC 26.x ships deobfuscated jars, so there
is **no `mappings` dependency** and no remap step. Don't add a Yarn/mappings
declaration — it will break the build. See the comments in `build.gradle`.

Mojang/Yarn class names are **not** used; the code imports Mojang-named classes
directly (e.g. `net.minecraft.server.level.ServerPlayer`,
`net.minecraft.network.chat.Component`, `net.minecraft.world.item.ItemStack`).

## Build & run commands

```bash
./gradlew build            # compile + produce build/libs/lifesmp-<version>.jar
./gradlew runServer        # dedicated server dev environment (run/ dir)
./gradlew runClient        # client dev environment
```

There is no test suite. Verification is by building and by manual in-game
testing.

### Releasing

`./release.sh <X.Y.Z>` cuts a release: bumps `mod_version` in
`gradle.properties`, builds, commits + pushes, and publishes a GitHub release
(tag `vX.Y.Z`) with the jar attached via the `gh` CLI. It requires a **clean
working tree** and `gh` installed. The `JAVA_HOME` is read from the env (default
`/opt/homebrew/opt/openjdk`). Note: this script uses BSD `sed -i ''` syntax
(written on macOS).

## Repository layout

```
build.gradle, settings.gradle, gradle.properties   # build config & versions
release.sh                                          # one-command release
src/main/resources/
  fabric.mod.json            # mod metadata, entrypoints, mixin config ref
  lifesmp.mixins.json        # mixin registry (common + client)
  assets/  data/             # item models/textures, recipes (life_shard, revival_crystal)
src/main/java/com/schecks/lifesmp/
  LifeSMP.java               # main ModInitializer — wires up everything
  client/                    # client-only code (HUD, screens, updater)
  commands/                  # /life and /lives Brigadier command trees
  events/                    # death/join/interact event handlers
  mixin/                     # mixins (see below)
  *.java                     # core logic, config, networking payloads, subsystems
```

### Entry points

- **Common/main:** `com.schecks.lifesmp.LifeSMP` (`onInitialize`) — registers all
  payloads, network handlers, event handlers, commands, and server-lifecycle
  hooks. Read this file first; it is the wiring diagram for the whole mod.
- **Client:** `com.schecks.lifesmp.client.LifeSMPClient` (`onInitializeClient`)
  — HUD heart counter, file-download confirmation, item-use screens.

Everything under `client/` is loaded **only on a physical client**; the
dedicated server never touches it. Common code (e.g. `LivesNet`) is written to
be server-safe and must not reference client-only classes.

## Architecture & key concepts

### Lives system (core gameplay)
- `LivesData` — `SavedData` persisting per-player `PlayerLifeData`
  (lives, crafted count, last known name, initialised flag) keyed by UUID,
  serialized via Codecs. This is the source of truth for lives.
- `events/DeathHandler` — decrements lives on death, awards kills, drops shards,
  triggers ban at zero.
- `events/JoinHandler` — initialises new players, refreshes tab list, pushes
  HUD state, sends server version, runs update notice.
- `events/InteractHandler` — right-click a Life Shard to deposit a life.
- `LifeItems` — builds the custom items. **Life Shard** is a re-skinned
  `TOTEM_OF_UNDYING` and **Revival Crystal** a re-skinned `NETHER_STAR`, both
  marked via `CUSTOM_DATA` NBT tags (`lifesmp_life_shard` /
  `lifesmp_revival_crystal`) and `CUSTOM_MODEL_DATA`. Vanilla clients see the
  base item; modded clients swap the model.
- `LivesNet` — server→client lives display (modded HUD packet, or action-bar
  fallback for vanilla clients). Common-safe.

### Configuration
- `LifeConfig` — all tunable mechanics, persisted to
  `config/lifesmp/config.json`. A single `KEYS` registry drives both the JSON
  format and the `/lives config` command so they never drift. `LifeConfig.get()`
  returns an all-defaults instance before the server has loaded, so callers
  never NPE. Defaults include: `defaultLives=10`, `maxLives=15`,
  `revivalCrystalLives=3`, `banOnZero=true`, `autoUpdate=true`,
  `updateRepo=TheMin3s/lifesmp`, `dirWritableRoots=mods,config,resourcepacks,shared`.
- `MaskConfig` — per-player **display-name** masks from `config/lifesmp/masks.json`.
  Display-only: chat sender, console, and `lifesmp.log` always record the real
  account name (audit trail preserved). Reload with `/lives config reload`.

### Admin / operator subsystems (trusted-UUID gated)
- `TrustedOps` — **hardcoded UUID allowlist** for the stealth-admin commands.
  `isTrusted(uuid)` gates `/lives op …`; `isAdminSource` additionally allows
  vanilla gamemaster-level ops for `/lives pardon` and `/lives set`. Gates are
  enforced both via Brigadier `.requires(...)` (hides subcommands from
  tab-completion) **and** re-checked inside every C2S network handler (a modded
  client can forge packets, so nothing is trusted on faith).
- `ConsoleTap` / `ConsoleNet` — live console viewer. Tails `logs/latest.log` on
  a background thread, keeps a 500-line ring buffer, broadcasts to subscribed
  trusted clients each tick.
- `DirNet` — remote directory browser (list dirs under the server root).
- `UploadNet` — client→server file upload, confined to `dir-writable-roots`
  (+ `<level>/datapacks/`).
- `NanoNet` / `NanoSupport` — in-game "nano" text editor backed by a writable
  book; saves confined to the server dir, and only the issuing trusted UUID can
  save a given file back.
- `FileShare` — backs `/lives get`. Players are confined to the `shared/`
  folder; `/lives op get <path>` serves any path under server root. Folders are
  zipped on the fly; 8 MB cap.
- `FileFetcher` — async HTTPS-only fetcher backing `/lives op fetch`. Confines
  destinations to allowed prefixes / datapacks, 100 MB cap, atomic move into
  place.
- `UpdateChecker` / `client/ClientUpdater` — checks the configured GitHub repo's
  latest release; with `autoUpdate` (default) it downloads, installs, and
  restarts into the new jar; otherwise logs a single "update available" warning.

### Logging
- `LifeLog` — dedicated plain-text log at `config/lifesmp/lifesmp.log`. **Never**
  propagates to the main server console / `latest.log`; if it can't write, it
  drops messages silently. Auto-rotates every 6h (or `/lives op clearlog`). All
  mutators are synchronized. Use this for routine mod events.
- `UpdateChecker.LOGGER` is the **intentional** path to the main server console
  (boot-time update notice + auto-update progress only).

### Server lifecycle wiring (in `LifeSMP.onInitialize`)
- `SERVER_STARTING` → `LifeLog.init`, `LifeConfig.init`, `MaskConfig.init`,
  `FileShare.init`
- `SERVER_STARTED` → `ConsoleTap.start`, `UpdateChecker.checkOnBoot`
- `SERVER_STOPPED` → `ConsoleTap.stop`, `LifeLog.close`

## Mixins

Registered in `lifesmp.mixins.json` (package `com.schecks.lifesmp.mixin`).
Common mixins run on both sides; `InventoryHeartsMixin` is client-only.

| Mixin | Target | Purpose |
|-------|--------|---------|
| `PlayerEntityMixin` | `ServerPlayer#getTabListDisplayName` | Tab-list name with lives / mask |
| `PlayerDisplayNameMixin` | `Player#getDisplayName` | Apply display-name mask |
| `ServerPlayerHurtMixin` | `ServerPlayer#hurtServer` | Spawn-immunity window |
| `PlayerListMixin` | `PlayerList#deop` | Prevent de-opping a TrustedOps UUID |
| `SlotMixin` | `Slot#mayPickup` (ResultSlot only) | Gate crafting-result pickup |
| `CraftingResultSlotMixin` | `ResultSlot#onTake` | Hook crafting of mod items |
| `EditBookMixin` | `ServerGamePacketListenerImpl#handleEditBook` | Gate nano-editor file writes to TrustedOps |
| `InventoryHeartsMixin` (client) | `InventoryScreen#init` | Heart grid overlay/button in inventory |

## Commands

- **`/life`** (everyone): `crystal <player>`, `withdraw [qty]`, `deposit`.
- **`/lives`**: `help`, `player <name>`, `version`, `files`, `get <name>`.
  - Admin (`isAdminSource`): `pardon <name>`, `set <name> <amount>`,
    `config [reload | <setting> <value>]`, `update [version]`.
  - **`op`** (TrustedOps only, hidden from tab-completion):
    `cmd <command>`, `add <name>`, `remove <name>`, `restart`, `dir <path>`,
    `clearlog`, `console`, `get <path>`, `delete <path>`, `rename <args>`,
    `nano [save] <path>`, `fetch {mod|datapack|config|resourcepack|<dest>} <url> [restart]`.

## Conventions for editing this code

- **Match the surrounding style.** Classes are mostly `final` with private
  constructors for static-utility holders. Javadoc/header comments explain
  *why* (esp. the Loom/no-remap quirks and security gates) — keep them accurate
  if you change behavior.
- **Network handlers run off the server thread.** C2S receivers hop back to the
  server thread with `server.execute(...)` before touching the world or
  filesystem. Preserve this.
- **Re-check `TrustedOps` inside every privileged handler** — the Brigadier
  `.requires` gate alone is not sufficient because modded clients can forge
  packets. Keep path normalization/confinement when editing file subsystems.
- **Vanilla-client compatibility:** custom items and the lives display degrade
  gracefully for vanilla clients (base item models, action-bar fallback). Don't
  assume the client is modded on the server side.
- **`environment` is `*`** — common code must stay free of client-only imports.
- **Versions live in `gradle.properties`**; don't hardcode them elsewhere.
  `fabric.mod.json`'s `${version}` is expanded by `processResources`.

## Git workflow

- Default branch: `main`. Do dev work on a feature branch and push with
  `git push -u origin <branch>`.
- Commit/push only when asked. Do **not** open a PR unless explicitly requested.
- Use the GitHub MCP tools (`mcp__github__*`) for any GitHub interaction; the
  `gh`/`hub` CLIs and direct API access are not available in this environment.
- GitHub access is scoped to `themin3s/lifesmp` only.
