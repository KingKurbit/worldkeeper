# Verification — Worldkeeper 0.1.0

Tested on Windows with Temurin Java 21.0.10, Minecraft 1.21.1, Fabric Loader 0.16.14, Fabric API 0.116.6+1.21.1. No personal Minecraft saves were accessed. All game fixtures were generated under this project's development directories.

## Results

- **Release build: PASS** — `gradlew.bat build`, installable remapped JAR and source JAR produced.
- **Automated tests: 19 passed, 0 failed** — region boundaries and negative block coordinates; inclusive/reversed corners; overlapping protection and buffers; automatic player safety; limits; source/destination validation; unchanged source SHA-256 hashes excluding Minecraft's session lock; preserved seed; terrain/entity/POI deletion; retained records in the same region file; oversized external `.mcc` payload removal; distinct repeated previews and preview-of-preview; locked source; cancellation before and during copying; failed/interrupted staging isolation; preservation of an earlier completed preview; empty mod directories.
- **Actual Minecraft integrated-server test: PASS, twice** — launched the Minecraft client, created a fresh world, populated a fixture, opened the Worldkeeper editor, invoked its real Create Preview and confirmation buttons, waited for save/close/copy, opened the result through Minecraft's world loader, and checked world state on the server thread.
- **World-list publication: PASS** — both separately named previews appeared alongside their retained synthetic source worlds in Minecraft's Singleplayer list.
- **Visual review: PASS at 854×480 / GUI scale 2** — editor, completion screen, world list, and playable preview were captured. A small summary-text overlap was corrected and rechecked.

Runtime fixture: a 5×5 oak-plank house in chunk (-2,-2), a protected chest at (-27,101,-27) holding exactly 17 diamonds, an underground stone-brick room with an emerald marker at (-27,40,-27), and an overlapping protected rectangle. The protection buffer was 1 chunk. Regeneration covered (-5,-5) through (5,5), crossing both coordinate axes: **121 selected, 12 named-protected including buffer, 31 excluded including player safety, 90 eligible**.

A gold marker, armor-stand entity, and explicitly registered bed POI were placed in eligible chunk (-4,-4). After opening the preview, the gold marker was gone and terrain had regenerated; the old entity UUID and bed POI were absent. The house, chest contents, underground marker, and player entry platform at Y=100 remained. The player entered at (8.5,101,8.5). The source remained separately available.

Evidence is in `verification/`: `runtime-result.txt`, `runtime.log`, game screenshots, and JUnit XML results. The automated storage tests compare source file hashes before and after the copy operation; the in-game fixture checks source retention and preview contents. It does not claim that ordinary save-and-close leaves the source byte-identical to its earlier running state.

## Reproduce

1. Install JDK 21 and extract the source ZIP.
2. Run `gradlew.bat build` for the unit/storage tests.
3. Run `gradlew.bat -Psmoke runClient` for the in-game fixture. A display and working OpenGL are required. The client creates uniquely named worlds under `build/smoke-run/saves`, drives the preview workflow, writes `build/smoke-run/smoke-result.txt`, then exits. Check that the result starts with `PASS`; a client exit alone does not establish success.
4. Run `gradlew.bat build` **without** `-Psmoke` before distributing. The opt-in smoke source and entrypoint are excluded from ordinary builds. Do not distribute a build made with `-Psmoke`.

The runtime harness uses real game APIs and invokes the actual widgets programmatically. Keyboard corner selection, rename/delete/pagination and cancellation-button interaction were not separately exercised by physical input automation; their underlying selection and cancellation behavior has automated coverage.

## Manual acceptance procedure

Use a **new disposable world**, cheats enabled for setting up the fixture, on 1.21.1 with the intended modpack. Do not begin with a personal survival save.

1. Build a small house and chest in one chunk; put a known item count in it. Build a marked underground room in that chunk. Note their block coordinates.
2. Stand at the two corners and press J at each; press K, enter a meaningful name, and Add protection. Test precise chunk-coordinate entry too. Add an overlapping protection. Set buffer to 1 and Save buffer. Click a list name, rename it, and delete an unneeded test protection.
3. Select a regeneration rectangle that crosses X=0 and Z=0, overlaps protection, and includes an unprotected marker, armor stand and bed. Keep its inclusive area at or below 256 chunks. Stand in a safe area outside the chunks you want regenerated.
4. Check the colored outlines and counts. Attempt 257+ chunks and confirm the limit is rejected. Confirm player safety remains even at buffer 0.
5. Create a preview. Confirm the source saves/closes; wait for the completion message. In the world list, verify that source and uniquely named preview both exist.
6. Open the preview. Inspect house, chest count, underground room and player position. Visit eligible chunks and verify the unprotected marker/entity/bed disappeared and replacement terrain generated. Inspect boundaries and structures for seams or cut features.
7. Save and quit; reopen the original. Confirm the original unprotected marker is still present and the saved Worldkeeper selections reload. Make a second preview and verify distinct names.
8. For cancellation, use a large **disposable** source and press Cancel while copying. Confirm no new world appears; inspect the staging marker. Retry successfully and confirm an earlier completed preview stays intact.
9. For interruption, stop only the disposable test client during copying. On restart, verify partial staging is outside the world list. Retry from the source. Never publish the partial folder manually.

## Remaining verification limits

- Third-party world-generation mods, custom global/sidecar chunk data, and large modpacks have not been tested.
- Long-term village behavior, structures spanning boundaries, feature spillover and terrain seams need broader fixtures. Clearing vanilla POI records does not establish universal village/structure correctness.
- Real disk exhaustion, OS power loss, network/removable filesystems, and every filesystem's atomic-rename semantics were not fault-injected. The code checks free space, handles IO failure, and fails closed if atomic publication is unsupported. Interrupted-state coverage simulates abandoned staging and tests cancellation during copying; it is not a power-loss durability guarantee.
- No cross-version migration, terrain blending, multiplayer, or change-import into the source is implemented.

The first-release milestone—opening a separate playable preview with protected contents intact and selected surrounding terrain regenerated—has been demonstrated in-game. Compatibility beyond the stated scope remains experimental.
