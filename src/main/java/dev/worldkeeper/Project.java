package dev.worldkeeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Project {
  public Regions.Rect selection = new Regions.Rect(-2, -2, 1, 1);
  public List<Regions.Protected> protectedRegions = new ArrayList<>();
  public int buffer = 1;
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

  public static Project load(Path source) throws IOException {
    Path file = source.resolve("worldkeeper.json");
    if (!Files.exists(file)) return new Project();
    try {
      Project p = JSON.fromJson(Files.readString(file), Project.class);
      if (p == null
          || p.selection == null
          || p.protectedRegions == null
          || p.buffer < 0
          || p.buffer > 16
          || p.protectedRegions.size() > 128) throw new IllegalArgumentException();
      for (var r : p.protectedRegions)
        if (r == null || r.rect() == null || r.name() == null) throw new IllegalArgumentException();
      return p;
    } catch (RuntimeException e) {
      throw new IOException("Invalid worldkeeper.json; restore or rename it before continuing", e);
    }
  }

  public void save(Path source) throws IOException {
    Path file = source.resolve("worldkeeper.json"), tmp = source.resolve("worldkeeper.json.tmp");
    Files.writeString(tmp, JSON.toJson(this));
    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }
}
