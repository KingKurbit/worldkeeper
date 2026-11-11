package dev.worldkeeper;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.SessionLock;
import net.minecraft.world.storage.*;

/** Only invoked after the integrated server has closed. Never edits source data. */
public final class PreviewOperation {
  public volatile String status = "Waiting for world to close";
  public volatile boolean done;
  public volatile Path result;
  public final AtomicBoolean cancel = new AtomicBoolean();

  private void check() {
    if (cancel.get()) throw new CancellationException();
  }

  public void run(Path game, Path original, List<Regions.Chunk> chunks) {
    Path stage = null;
    try {
      Path saves = game.resolve("saves").toRealPath();
      Path source = SavePaths.source(saves, original);
      Path staging = game.resolve("worldkeeper-staging");
      Files.createDirectories(staging);
      if (Files.isSymbolicLink(staging)
          || !staging.toRealPath().equals(staging.toAbsolutePath().normalize()))
        throw new IOException("Linked staging folders are unsupported");
      String id = UUID.randomUUID().toString();
      String name =
          "Worldkeeper Preview "
              + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"))
              + " "
              + id.substring(0, 8);
      Path target = saves.resolve(name);
      stage = staging.resolve(id);
      SavePaths.destination(source, stage);
      SavePaths.destination(source, target);
      // Minecraft's own OS lock prevents a second client or operation opening the source.
      try (SessionLock lock = SessionLock.create(source)) {
        check();
        status = "Scanning source size";
        List<Path> files = new ArrayList<>(), directories = new ArrayList<>();
        final long[] bytes = {0};
        Files.walkFileTree(
            source,
            new SimpleFileVisitor<>() {
              @Override
              public FileVisitResult preVisitDirectory(Path p, BasicFileAttributes a)
                  throws IOException {
                check();
                if (Files.isSymbolicLink(p) || !p.toRealPath().startsWith(source))
                  throw new IOException("Linked directories are unsupported");
                directories.add(p);
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(Path p, BasicFileAttributes a) throws IOException {
                check();
                if (!a.isRegularFile() || Files.isSymbolicLink(p))
                  throw new IOException("Unsupported file: " + p.getFileName());
                if (!p.getFileName().toString().equals("session.lock")
                    && !p.equals(source.resolve("worldkeeper-operation.txt"))) {
                  files.add(p);
                  bytes[0] = Math.addExact(bytes[0], a.size());
                }
                return FileVisitResult.CONTINUE;
              }
            });
        if (Files.getFileStore(staging).getUsableSpace() < bytes[0] + 268435456L)
          throw new IOException("Insufficient disk space: need world size plus 256 MiB");
        Files.createDirectory(stage);
        marker(stage, "COPYING");
        for (Path directory : directories) {
          check();
          Files.createDirectories(stage.resolve(source.relativize(directory)));
        }
        long copied = 0;
        byte[] buffer = new byte[1024 * 1024];
        for (Path file : files) {
          check();
          Path dest = stage.resolve(source.relativize(file));
          Files.createDirectories(dest.getParent());
          try (InputStream in = Files.newInputStream(file);
              OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE_NEW)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
              check();
              out.write(buffer, 0, n);
              copied += n;
              status = "Copying " + (copied / 1048576) + " / " + (bytes[0] / 1048576) + " MiB";
            }
          }
        }
        marker(stage, "REMOVING_CHUNKS");
        for (String kind : List.of("region", "entities", "poi")) {
          Map<String, List<Regions.Chunk>> groups = new HashMap<>();
          for (var c : chunks)
            groups
                .computeIfAbsent(
                    "r." + Math.floorDiv(c.x(), 32) + "." + Math.floorDiv(c.z(), 32) + ".mca",
                    k -> new ArrayList<>())
                .add(c);
          for (var entry : groups.entrySet()) {
            check();
            status = "Clearing " + kind + ": " + entry.getKey();
            Path directory = stage.resolve(kind), file = directory.resolve(entry.getKey());
            if (Files.exists(file))
              try (RegionFile region =
                  new RegionFile(
                      new StorageKey(name, World.OVERWORLD, kind), file, directory, true)) {
                for (var c : entry.getValue()) {
                  check();
                  region.delete(new ChunkPos(c.x(), c.z()));
                }
                region.sync();
              }
          }
        }
        check();
        status = "Naming preview";
        Path level = stage.resolve("level.dat");
        NbtCompound nbt = NbtIo.readCompressed(level, NbtSizeTracker.ofUnlimitedBytes());
        if (!nbt.contains("Data", 10))
          throw new IOException("Source level.dat has no world Data compound");
        nbt.getCompound("Data").putString("LevelName", name);
        NbtIo.writeCompressed(nbt, level);
        // Do not leave a fallback level.dat with the old display name.
        Files.copy(level, stage.resolve("level.dat_old"), StandardCopyOption.REPLACE_EXISTING);
        check();
        marker(stage, "READY");
        // Atomic directory publication: incomplete copies never enter the world list.
        SavePaths.destination(source, target);
        Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
        result = target;
        status = "Created " + name;
      }
    } catch (CancellationException e) {
      status = "Cancelled. Source kept; incomplete staging retained.";
      markFailure(stage, "CANCELLED");
    } catch (Exception e) {
      status = "Failed: " + e.getMessage();
      Worldkeeper.LOG.error("Preview failed; staging retained at {}", stage, e);
      markFailure(stage, "FAILED: " + e);
    } finally {
      done = true;
    }
  }

  private static void marker(Path stage, String phase) throws IOException {
    Path marker = stage.resolve("worldkeeper-operation.txt");
    Files.writeString(marker, "Worldkeeper 0.1.0\n" + phase + "\n");
    try (FileChannel channel = FileChannel.open(marker, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
  }

  private static void markFailure(Path stage, String state) {
    if (stage != null && Files.isDirectory(stage))
      try {
        marker(stage, state);
      } catch (IOException ignored) {
      }
  }
}
