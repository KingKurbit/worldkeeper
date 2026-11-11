package dev.worldkeeper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.SessionLock;
import net.minecraft.world.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreviewOperationTest {
  @TempDir Path game;

  Path source() throws Exception {
    Path source = Files.createDirectories(game.resolve("saves/source"));
    NbtCompound root = new NbtCompound(), data = new NbtCompound(), generation = new NbtCompound();
    generation.putLong("seed", 123456789L);
    data.put("WorldGenSettings", generation);
    data.putString("LevelName", "Synthetic source");
    root.put("Data", data);
    NbtIo.writeCompressed(root, source.resolve("level.dat"));
    for (String kind : List.of("region", "entities", "poi")) {
      Path dir = Files.createDirectory(source.resolve(kind));
      for (var pos : List.of(new ChunkPos(-1, -1), new ChunkPos(-2, -1), new ChunkPos(0, 0))) {
        Path file = dir.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
        try (var region =
                new RegionFile(
                    new StorageKey("synthetic", World.OVERWORLD, kind), file, dir, true);
            var out = region.getChunkOutputStream(pos)) {
          out.writeUTF(kind + " fixture at " + pos);
        }
      }
    }
    return source;
  }

  Map<String, String> hashes(Path root) throws Exception {
    Map<String, String> hashes = new TreeMap<>();
    try (var files = Files.walk(root)) {
      for (Path p : files.filter(Files::isRegularFile).toList())
        if (!p.getFileName().toString().equals("session.lock"))
          hashes.put(
              root.relativize(p).toString(),
              HexFormat.of()
                  .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
    }
    return hashes;
  }

  @Test
  void removesAllThreeStoresPreservesOthersAndSourceAndSeed() throws Exception {
    Path source = source();
    var before = hashes(source);
    var op = new PreviewOperation();
    op.run(game, source, List.of(new Regions.Chunk(-1, -1)));
    assertTrue(op.done);
    assertNotNull(op.result, op.status);
    assertEquals(before, hashes(source));
    for (String kind : List.of("region", "entities", "poi")) {
      Path dir = op.result.resolve(kind);
      try (var removed =
              new RegionFile(
                  new StorageKey("test", World.OVERWORLD, kind),
                  dir.resolve("r.-1.-1.mca"),
                  dir,
                  true);
          var retained =
              new RegionFile(
                  new StorageKey("test", World.OVERWORLD, kind),
                  dir.resolve("r.0.0.mca"),
                  dir,
                  true)) {
        assertNull(removed.getChunkInputStream(new ChunkPos(-1, -1)));
        try (var in = removed.getChunkInputStream(new ChunkPos(-2, -1))) {
          assertNotNull(in);
          assertEquals(kind + " fixture at " + new ChunkPos(-2, -1), in.readUTF());
        }
        try (var in = retained.getChunkInputStream(new ChunkPos(0, 0))) {
          assertNotNull(in);
          assertEquals(kind + " fixture at " + new ChunkPos(0, 0), in.readUTF());
        }
      }
    }
    var data =
        NbtIo.readCompressed(op.result.resolve("level.dat"), NbtSizeTracker.ofUnlimitedBytes())
            .getCompound("Data");
    assertEquals(123456789L, data.getCompound("WorldGenSettings").getLong("seed"));
    assertTrue(data.getString("LevelName").startsWith("Worldkeeper Preview"));
  }

  @Test
  void repeatedAndPreviewOfPreviewAreDistinct() throws Exception {
    Path source = source();
    var first = new PreviewOperation();
    first.run(game, source, List.of());
    var second = new PreviewOperation();
    second.run(game, source, List.of());
    assertNotNull(first.result, first.status);
    assertNotNull(second.result, second.status);
    assertNotEquals(first.result, second.result);
    var third = new PreviewOperation();
    third.run(game, first.result, List.of());
    assertNotNull(third.result, third.status);
  }

  @Test
  void cancelledOperationNeverPublishes() throws Exception {
    Path source = source();
    var before = hashes(source);
    var op = new PreviewOperation();
    op.cancel.set(true);
    op.run(game, source, List.of());
    assertNull(op.result);
    assertTrue(op.status.startsWith("Cancelled"));
    assertEquals(before, hashes(source));
    try (var worlds = Files.list(game.resolve("saves"))) {
      assertEquals(1, worlds.count());
    }
  }

  @Test
  void failureAndInterruptedStagingDoNotPublishOrDamageCompletedPreview() throws Exception {
    Path source = source();
    var good = new PreviewOperation();
    good.run(game, source, List.of());
    assertNotNull(good.result, good.status);
    var before = hashes(good.result);
    Path stale = Files.createDirectory(game.resolve("worldkeeper-staging/interrupted"));
    Files.writeString(stale.resolve("worldkeeper-operation.txt"), "COPYING");
    Files.writeString(stale.resolve("level.dat"), "incomplete");
    Files.writeString(source.resolve("level.dat"), "corrupt fixture");
    var bad = new PreviewOperation();
    bad.run(game, source, List.of());
    assertNull(bad.result);
    assertTrue(bad.status.startsWith("Failed"));
    assertEquals(before, hashes(good.result));
    assertTrue(Files.exists(stale));
    try (var worlds = Files.list(game.resolve("saves"))) {
      assertEquals(2, worlds.count());
    }
  }

  @Test
  void lockedSourceFailsClosed() throws Exception {
    Path source = source();
    try (var lock = SessionLock.create(source)) {
      var op = new PreviewOperation();
      op.run(game, source, List.of());
      assertNull(op.result);
      assertTrue(op.status.startsWith("Failed"));
    }
  }

  @Test
  void externalChunkPayloadIsRemovedWithItsHeader() throws Exception {
    Path source = source(), dir = source.resolve("region");
    byte[] payload = new byte[2 * 1024 * 1024];
    new Random(44).nextBytes(payload);
    try (var region =
            new RegionFile(
                new StorageKey("test", World.OVERWORLD, "region"),
                dir.resolve("r.-1.-1.mca"),
                dir,
                true);
        var out = region.getChunkOutputStream(new ChunkPos(-1, -1))) {
      out.write(payload);
    }
    assertTrue(Files.exists(dir.resolve("c.-1.-1.mcc")));
    var op = new PreviewOperation();
    op.run(game, source, List.of(new Regions.Chunk(-1, -1)));
    assertNotNull(op.result, op.status);
    assertFalse(Files.exists(op.result.resolve("region/c.-1.-1.mcc")));
    assertTrue(Files.exists(dir.resolve("c.-1.-1.mcc")));
  }

  @Test
  void cancellationDuringCopyLeavesOnlyStaging() throws Exception {
    Path source = source();
    byte[] payload = new byte[1024 * 1024];
    for (int i = 0; i < 64; i++) Files.write(source.resolve("fixture-" + i), payload);
    var op = new PreviewOperation();
    Thread worker = new Thread(() -> op.run(game, source, List.of()));
    worker.start();
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (!op.status.startsWith("Copying") && !op.done && System.nanoTime() < deadline)
      Thread.yield();
    op.cancel.set(true);
    worker.join(10000);
    assertFalse(worker.isAlive());
    assertNull(op.result);
    assertTrue(op.status.startsWith("Cancelled"), op.status);
    try (var stages = Files.list(game.resolve("worldkeeper-staging"))) {
      var stage = stages.findFirst().orElseThrow();
      assertTrue(
          Files.readString(stage.resolve("worldkeeper-operation.txt")).contains("CANCELLED"));
    }
    try (var saves = Files.list(game.resolve("saves"))) {
      assertEquals(1, saves.count());
    }
  }

  @Test
  void emptyModDirectoriesArePreserved() throws Exception {
    Path source = source();
    Files.createDirectories(source.resolve("mod-data/empty"));
    var op = new PreviewOperation();
    op.run(game, source, List.of());
    assertNotNull(op.result, op.status);
    assertTrue(Files.isDirectory(op.result.resolve("mod-data/empty")));
  }
}
