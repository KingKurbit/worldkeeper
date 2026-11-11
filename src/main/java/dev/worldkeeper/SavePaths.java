package dev.worldkeeper;

import java.io.IOException;
import java.nio.file.*;

public final class SavePaths {
  public static Path source(Path saves, Path source) throws IOException {
    Path root = saves.toRealPath(), real = source.toRealPath();
    if (!real.getParent().equals(root) || Files.isSymbolicLink(source))
      throw new IOException("Source must be a direct, non-linked save folder");
    return real;
  }

  public static void destination(Path source, Path destination) throws IOException {
    Path s = source.toRealPath(), d = destination.toAbsolutePath().normalize();
    if (Files.exists(d)
        || d.startsWith(s)
        || s.startsWith(d)
        || !d.getParent().toRealPath().equals(d.getParent()))
      throw new IOException("Unsafe or existing destination");
  }

  public static boolean incomplete(Path stage) {
    return Files.isRegularFile(stage.resolve("worldkeeper-operation.txt"));
  }

  private SavePaths() {}
}
