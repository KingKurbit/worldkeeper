# Worldkeeper 0.1.0

A Fabric client mod for **Minecraft Java 1.21.1**, **Java 21**, private singleplayer, and the Overworld. Create a separate playable copy with selected chunks removed so Minecraft can generate them again. The source remains available.

## Install and try

1. Use a Minecraft **1.21.1** Fabric installation with Fabric Loader **0.16.14 or newer**.
2. Install **Fabric API for 1.21.1** (tested with 0.116.6+1.21.1).
3. Put `worldkeeper-0.1.0.jar` in that installation's `mods` folder.
4. Start with a disposable test world. Open **Worldkeeper** using **K** or its button at the top left of the pause menu. Keybindings are configurable under Controls → Worldkeeper.

Fabric installer: https://fabricmc.net/use/installer/
Fabric API: https://modrinth.com/mod/fabric-api/versions?g=1.21.1

## Select and protect

- Stand at one corner and press **J**, then move to the other corner and press **J** again. This selects whole chunks containing your feet, including both corners. Press **K** to review it.
- Alternatively enter **chunk X/Z coordinates**, not block coordinates. Convert with floor(block / 16): block -1 is chunk -1, block -17 is chunk -2.
- **Set regeneration** applies the coordinate fields to the regeneration rectangle. **Add protection** creates a named protected rectangle from those fields. Use a name before adding.
- Click a protection-list name to inspect its coordinates. Enter a new name in the name field and click that row's **Rename**. **Delete** removes the protection. Arrow buttons page through the list.
- **Save buffer** applies the buffer field to every protected rectangle. Default: 1 chunk. Range: 0–16. Protection always wins.
- Outlines: **orange** regeneration, **green** protected rectangle, **blue** protection buffer, **purple** automatic player safety. Outlines are terrain-depth-tested and drawn around the player's current height; protection covers the entire vertical chunk.
- The summary counts chunks within the regeneration selection. “Protected” counts the union of named protection and its buffer; “excluded” also includes player safety; “regen” is what will be removed. Overlaps are counted once.
- The current player chunk plus **two chunks in every direction** (5×5 chunks) is always excluded. That buffer cannot be disabled. The authoritative player position is captured when the operation starts.
- Default regeneration selection: 4×4 chunks near the origin. Maximum rectangle: **256 chunks**, including protected/excluded chunks. Maximum protected regions: 128. Choose a small area away from the player if all chunks are excluded.

Selections are saved in the source world's `worldkeeper.json`. Use “Set regeneration”, “Add protection”, “Save buffer”, rename, or delete to apply edits; unsubmitted text fields are not applied by Create Preview.

## Create and inspect the preview

Click **Create Preview World**, then **Save & create**. Minecraft saves and closes the singleplayer world. Worldkeeper obtains Minecraft's OS session lock, scans and copies the closed save in a background thread, and removes eligible Overworld chunk records from `region`, `entities`, and `poi` using Minecraft's region-file API. Seed, datapacks, player data, dimensions, and world-generation configuration are copied.

The result appears in Singleplayer with a name such as `Worldkeeper Preview 2026-11-10 22-30-00 a12bc345`. Click **Cancel / Back** after completion to see the world list, then open the preview normally. **Replacement terrain generates when Minecraft loads those chunks**, not during copying. Keep the preview as your new playable world if satisfied. There is no import-back operation.

New content appears only where the copied generation settings, seed, and currently installed compatible mods can generate it. Adding a mod does not necessarily update a world's existing biome source or generator configuration. This mod does not perform cross-version migration or guarantee compatibility with every world-generation mod.

## Reliability and recovery

- Never overwrites a source save or an existing preview. The ordinary save-and-close step and selection persistence update the source as expected; copying and regeneration do not modify its world data. Minecraft's lock file may change.
- Rejects linked source roots, linked/unsupported files, nested destinations, and existing destinations. The source must be directly under this instance's `saves` directory.
- Requires free disk space for the complete world plus a 256 MiB reserve. Each preview copies the **entire save**, even for a small selection. Copy-time IO errors also fail closed.
- Uses the game's exclusive session lock to prevent another client or preview operation opening the same source during copying.
- Incomplete saves stay under `<game directory>/worldkeeper-staging/<UUID>/`, outside the world list. A forced operation marker records copying, removal, readiness, cancellation, or failure. Only a successful atomic directory move publishes a save under `saves`.
- Cancellation is checked during scanning, each 1 MiB copy block, and each chunk removal. Saving/closing and final publication are not cancellable. A cancel arriving after publication leaves the completed preview intact.
- Failed/interrupted staging is retained for diagnosis; it is **never resumed or automatically published**. Reopen the source and retry to create a fresh preview. With Minecraft closed, manually delete only unwanted UUID directories under `worldkeeper-staging` to reclaim space. Do not move partial staging into `saves`.
- A successful preview includes a `READY` operation marker. Location under `saves` is the publication boundary; a `READY` marker under staging after a crash is still an unpublished operation.
- Failure details are logged with the staging path in Minecraft's `logs/latest.log`. World contents are not uploaded or published.

## Limits

- No multiplayer/LAN, Nether/End regeneration, individual-block preservation, build detection, custom terrain blending, or cross-version conversion.
- Chunk boundaries may have visible seams. Structures can span chunks; existing structure references, maps, raids, and other saved global data are copied, not rebuilt. Structures or features can be cut at the boundary.
- Protection prevents deletion of protected chunks. Vanilla/mod generation, neighboring feature placement, fluid flow, entity movement, and normal simulation after opening can still affect nearby blocks or entities. Use generous protection buffers around builds and inspect the preview before adopting it.
- Vanilla terrain, entity, and POI stores are cleared together. Mod-specific chunk metadata in other files is not understood or reset. Validate the exact modpack using disposable worlds first.
- Player safety preserves terrain around the saved player location, but does not rescue an already unsafe position, protect all respawn locations, or guarantee safety from mobs or hazards. Start from a stable sheltered location.
- Very large GUI scales may require reducing GUI Scale in Video Settings; the editor needs at least 320×240 logical pixels.

## Build and tests

On Windows with JDK 21: `gradlew.bat build`. On Unix: `./gradlew build` (set executable permission if needed). The wrapper uses Gradle 9.2.1; Fabric Loom 1.15.4 and dependencies are pinned. First build downloads dependencies.

Installable artifact: `build/libs/worldkeeper-0.1.0.jar`. Source JAR: `build/libs/worldkeeper-0.1.0-sources.jar`. Run a disposable development client with `gradlew.bat runClient`; it uses the project's `run` directory, not your personal Minecraft installation. Run `genSources` separately from `build` if you want decompiled development sources.

See `VERIFICATION.md` for automated results, actual runtime status, and the manual fixture procedure.

## Next milestones

1. Expand real-world fixture coverage for structures, villages, custom biome sources, and large modpacks.
2. Add a chunk-map overview with clearer buffer and exclusion visualization.
3. Add staging inspection/reclamation UI and space estimates before closing the world.
4. Investigate opt-in pre-generation and mod-specific metadata adapters without weakening source isolation.

## Technical references

- Fabric's versioned 1.21.1 setup documentation: https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.1/develop/getting-started/setting-up-a-development-environment.md
- Client disconnect lifecycle: https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/client/MinecraftClient.html
- Save session and locking: https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/world/level/storage/LevelStorage.Session.html
- Region-file deletion and external chunk storage: https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/world/storage/RegionFile.html

Minecraft 1.21.1 was selected for its Java 21 support, versioned Fabric documentation, and established tooling. Minecraft classes are used through the game runtime, not redistributed in this source or mod JAR. Fabric API is a separate dependency.
