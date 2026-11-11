package dev.worldkeeper;

import java.util.*;

public final class Regions {
  public static final int LIMIT = 256;

  public record Chunk(int x, int z) {}

  public record Rect(int minX, int minZ, int maxX, int maxZ) {
    public Rect {
      if (minX > maxX
          || minZ > maxZ
          || minX < -1875000
          || minZ < -1875000
          || maxX > 1874999
          || maxZ > 1874999)
        throw new IllegalArgumentException("Invalid chunk rectangle / world border");
    }

    public static Rect corners(int x, int z, int xx, int zz) {
      return new Rect(Math.min(x, xx), Math.min(z, zz), Math.max(x, xx), Math.max(z, zz));
    }

    public static int chunk(int block) {
      return Math.floorDiv(block, 16);
    }

    public long area() {
      return ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
    }

    public boolean contains(Chunk c, int buffer) {
      return c.x >= (long) minX - buffer
          && c.x <= (long) maxX + buffer
          && c.z >= (long) minZ - buffer
          && c.z <= (long) maxZ + buffer;
    }
  }

  public record Protected(String name, Rect rect) {}

  public static List<Chunk> eligible(
      Rect selection, List<Protected> protectedRegions, int buffer, Chunk player) {
    if (buffer < 0 || buffer > 16) throw new IllegalArgumentException("Buffer must be 0–16 chunks");
    if (selection.area() > LIMIT)
      throw new IllegalArgumentException("Maximum selection: 256 chunks");
    List<Chunk> out = new ArrayList<>();
    for (int x = selection.minX; x <= selection.maxX; x++)
      for (int z = selection.minZ; z <= selection.maxZ; z++) {
        Chunk c = new Chunk(x, z);
        boolean safe = Math.abs((long) x - player.x) <= 2 && Math.abs((long) z - player.z) <= 2;
        if (!safe && protectedRegions.stream().noneMatch(p -> p.rect.contains(c, buffer)))
          out.add(c);
      }
    return List.copyOf(out);
  }

  private Regions() {}
}
