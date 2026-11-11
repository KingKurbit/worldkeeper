package dev.worldkeeper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavePathsTest {
  @TempDir Path temp;

  @Test
  void acceptsOnlyDirectSaveChildren() throws Exception {
    Path saves = Files.createDirectory(temp.resolve("saves"));
    Path source = Files.createDirectory(saves.resolve("source"));
    assertEquals(source.toRealPath(), SavePaths.source(saves, source));
    assertThrows(IOException.class, () -> SavePaths.source(saves, temp));
  }

  @Test
  void rejectsSelfExistingAndRecursiveDestination() throws Exception {
    Path source = Files.createDirectory(temp.resolve("source"));
    assertThrows(IOException.class, () -> SavePaths.destination(source, source));
    assertThrows(IOException.class, () -> SavePaths.destination(source, source.resolve("copy")));
    assertThrows(IOException.class, () -> SavePaths.destination(source, temp));
    SavePaths.destination(source, temp.resolve("preview"));
  }

  @Test
  void rejectsUnresolvedParent() throws Exception {
    Path source = Files.createDirectory(temp.resolve("source"));
    assertThrows(
        IOException.class,
        () -> SavePaths.destination(source, temp.resolve("missing").resolve("preview")));
  }

  @Test
  void interruptedStagingIsRecognizableWithoutLevelData() throws Exception {
    Path stage = Files.createDirectory(temp.resolve("operation"));
    Files.writeString(stage.resolve("worldkeeper-operation.txt"), "Worldkeeper 0.1.0\nCOPYING\n");
    assertTrue(SavePaths.incomplete(stage));
    assertFalse(Files.exists(stage.resolve("level.dat")));
  }
}
