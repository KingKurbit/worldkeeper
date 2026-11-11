package dev.worldkeeper;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;

/** Disposable in-game integration fixture. Enabled only by Gradle -Psmoke. */
public class SmokeTest implements ClientModInitializer {
  int stage = 0, ticks = 0;
  String sourceName = "WorldkeeperSynthetic-" + System.currentTimeMillis();
  UUID removedEntity;
  final BlockPos chest = new BlockPos(-27, 101, -27),
      underground = new BlockPos(-27, 40, -27),
      marker = new BlockPos(-60, 150, -60),
      bed = new BlockPos(-59, 151, -60);

  void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  void report(MinecraftClient c, String text) {
    try {
      Files.writeString(c.runDirectory.toPath().resolve("smoke-result.txt"), text);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    Worldkeeper.LOG.info("SMOKE: {}", text);
  }

  void shot(MinecraftClient c, String name) {
    ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), t -> {});
  }

  void press(MinecraftClient c, String label) {
    var button =
        c.currentScreen.children().stream()
            .filter(e -> e instanceof ButtonWidget b && b.getMessage().getString().equals(label))
            .map(e -> (ButtonWidget) e)
            .findFirst()
            .orElseThrow();
    button.onPress();
  }

  @Override
  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          try {
            ticks++;
            if (stage == 0 && c.getOverlay() == null && c.currentScreen != null && ticks > 80) {
              stage = 1;
              report(c, "RUNNING: creating isolated synthetic world");
              c.options.getViewDistance().setValue(5);
              c.options.getSimulationDistance().setValue(5);
              c.createIntegratedServerLoader()
                  .createAndStart(
                      sourceName,
                      MinecraftServer.DEMO_LEVEL_INFO,
                      GeneratorOptions.DEMO_OPTIONS,
                      WorldPresets::createDemoOptions,
                      new TitleScreen());
            } else if (stage == 1 && c.player != null && c.getServer() != null) {
              stage = 2;
              c.getServer()
                  .execute(
                      () -> {
                        try {
                          var server = c.getServer();
                          var w = server.getOverworld();
                          var player = server.getPlayerManager().getPlayer(c.player.getUuid());
                          for (int x = -29; x <= -25; x++)
                            for (int z = -29; z <= -25; z++)
                              for (int y = 100; y <= 104; y++)
                                w.setBlockState(
                                    new BlockPos(x, y, z),
                                    (y == 100 || y == 104 || x == -29 || x == -25 || z == -29
                                            || z == -25)
                                        ? Blocks.OAK_PLANKS.getDefaultState()
                                        : Blocks.AIR.getDefaultState());
                          w.setBlockState(chest, Blocks.CHEST.getDefaultState());
                          ((ChestBlockEntity) w.getBlockEntity(chest))
                              .setStack(0, new ItemStack(Items.DIAMOND, 17));
                          for (int x = -29; x <= -25; x++)
                            for (int z = -29; z <= -25; z++)
                              for (int y = 39; y <= 42; y++)
                                w.setBlockState(
                                    new BlockPos(x, y, z),
                                    (y == 39 || y == 42 || x == -29 || x == -25 || z == -29
                                            || z == -25)
                                        ? Blocks.STONE_BRICKS.getDefaultState()
                                        : Blocks.AIR.getDefaultState());
                          w.setBlockState(underground, Blocks.EMERALD_BLOCK.getDefaultState());
                          for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                              w.setBlockState(
                                  new BlockPos(x, 100, z), Blocks.STONE_BRICKS.getDefaultState());
                          w.setBlockState(marker, Blocks.GOLD_BLOCK.getDefaultState());
                          var bedState =
                              Blocks.RED_BED.getDefaultState().with(BedBlock.PART, BedPart.HEAD);
                          w.setBlockState(bed, bedState);
                          // Force the real bed POI into the fixture, then verify its persisted
                          // removal.
                          w.getPointOfInterestStorage()
                              .add(
                                  bed,
                                  net.minecraft.world.poi.PointOfInterestTypes.getTypeForState(
                                          bedState)
                                      .orElseThrow());
                          require(
                              w.getPointOfInterestStorage().getType(bed).isPresent(),
                              "Fixture POI missing");
                          ArmorStandEntity entity = new ArmorStandEntity(w, -60, 151, -60);
                          removedEntity = entity.getUuid();
                          w.spawnEntity(entity);
                          player.teleport(w, 8.5, 101, 8.5, 0, 0);
                          c.execute(
                              () -> {
                                try {
                                  Worldkeeper.load(c);
                                  var p = Worldkeeper.project;
                                  p.selection = new Regions.Rect(-5, -5, 5, 5);
                                  p.buffer = 1;
                                  p.protectedRegions.add(
                                      new Regions.Protected(
                                          "House + underground", new Regions.Rect(-2, -2, -2, -2)));
                                  p.protectedRegions.add(
                                      new Regions.Protected(
                                          "Overlap", new Regions.Rect(-1, -2, -1, -2)));
                                  p.save(Worldkeeper.source);
                                  stage = 3;
                                  ticks = 0;
                                  c.setScreen(null);
                                } catch (Exception e) {
                                  fail(c, e);
                                }
                              });
                        } catch (Throwable e) {
                          c.execute(() -> fail(c, e));
                        }
                      });
            } else if (stage == 3 && ticks > 80) {
              Worldkeeper.open(c);
              stage = 4;
              ticks = 0;
            } else if (stage == 4 && ticks > 20) {
              shot(c, "worldkeeper-editor.png");
              press(c, "Create Preview World");
              stage = 5;
              ticks = 0;
            } else if (stage == 5 && ticks > 10) {
              press(c, "Save & create");
              stage = 6;
            } else if (stage == 6 && Worldkeeper.operation != null && Worldkeeper.operation.done) {
              var op = Worldkeeper.operation;
              require(op.result != null, op.status);
              stage = 61;
              ticks = 0;
            } else if (stage == 61 && ticks > 20) {
              shot(c, "worldkeeper-complete.png");
              press(c, "Cancel / Back");
              stage = 62;
              ticks = 0;
            } else if (stage == 62 && ticks > 30) {
              shot(c, "worldkeeper-world-list.png");
              report(
                  c,
                  "RUNNING: preview published; opening "
                      + Worldkeeper.operation.result.getFileName());
              stage = 7;
              c.createIntegratedServerLoader()
                  .start(
                      Worldkeeper.operation.result.getFileName().toString(),
                      () -> fail(c, new AssertionError("Preview load cancelled")));
            } else if (stage == 7 && c.player != null && c.getServer() != null) {
              stage = 8;
              ticks = 0;
              c.getServer()
                  .execute(
                      () -> {
                        try {
                          var w = c.getServer().getOverworld();
                          var player =
                              c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                          require(
                              w.getBlockState(new BlockPos(-29, 100, -29)).isOf(Blocks.OAK_PLANKS),
                              "House missing");
                          var box = (ChestBlockEntity) w.getBlockEntity(chest);
                          require(
                              box != null
                                  && box.getStack(0).isOf(Items.DIAMOND)
                                  && box.getStack(0).getCount() == 17,
                              "Protected chest contents changed");
                          require(
                              w.getBlockState(underground).isOf(Blocks.EMERALD_BLOCK),
                              "Underground marker missing");
                          require(
                              !w.getBlockState(marker).isOf(Blocks.GOLD_BLOCK),
                              "Eligible chunk did not regenerate");
                          require(w.getEntity(removedEntity) == null, "Stale entity survived");
                          require(
                              w.getPointOfInterestStorage().getType(bed).isEmpty(),
                              "Stale bed POI survived");
                          require(
                              w.getBlockState(new BlockPos(8, 100, 8)).isOf(Blocks.STONE_BRICKS)
                                  && player.getY() >= 100,
                              "Unsafe player entry");
                          require(
                              Files.isRegularFile(
                                  c.runDirectory
                                      .toPath()
                                      .resolve("saves")
                                      .resolve(sourceName)
                                      .resolve("level.dat")),
                              "Source missing");
                          c.execute(
                              () -> {
                                report(
                                    c,
                                    "PASS: real integrated-server preview opened; protected house,"
                                        + " 17 diamonds, underground marker and entry platform"
                                        + " intact; eligible chunk regenerated; old entity UUID and"
                                        + " POI absent. Source retained. Preview="
                                        + Worldkeeper.operation.result.getFileName());
                                stage = 9;
                                ticks = 0;
                                c.setScreen(null);
                              });
                        } catch (Throwable e) {
                          c.execute(() -> fail(c, e));
                        }
                      });
            } else if (stage == 9 && ticks > 40) {
              shot(c, "worldkeeper-preview-open.png");
              stage = 10;
              c.world.disconnect();
              c.disconnect();
              c.scheduleStop();
            }
          } catch (Throwable e) {
            fail(c, e);
          }
        });
  }

  void fail(MinecraftClient c, Throwable e) {
    stage = 99;
    Worldkeeper.LOG.error("Smoke failure", e);
    report(c, "FAIL: " + e);
    c.scheduleStop();
  }
}
