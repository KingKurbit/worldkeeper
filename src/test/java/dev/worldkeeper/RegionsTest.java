package dev.worldkeeper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RegionsTest {
  @Test
  void negativeBlockCoordinatesFloor() {
    assertEquals(-1, Regions.Rect.chunk(-1));
    assertEquals(-1, Regions.Rect.chunk(-16));
    assertEquals(-2, Regions.Rect.chunk(-17));
    assertEquals(0, Regions.Rect.chunk(15));
    assertEquals(1, Regions.Rect.chunk(16));
  }

  @Test
  void reversedInclusiveCorners() {
    var r = Regions.Rect.corners(1, 2, -1, -2);
    assertEquals(15, r.area());
    assertTrue(r.contains(new Regions.Chunk(-1, -2), 0));
    assertFalse(r.contains(new Regions.Chunk(-2, -2), 0));
  }

  @Test
  void protectionAndBufferWinAcrossZero() {
    var selection = new Regions.Rect(-3, -3, 3, 3);
    var p =
        List.of(
            new Regions.Protected("House", new Regions.Rect(-1, -1, 0, 0)),
            new Regions.Protected("Overlap", new Regions.Rect(0, 0, 1, 1)));
    var result = Regions.eligible(selection, p, 1, new Regions.Chunk(100, 100));
    assertEquals(26, result.size()); // two 4x4 buffered squares overlap in a 3x3 square: union 23
    assertFalse(result.contains(new Regions.Chunk(-2, -2)));
    assertFalse(result.contains(new Regions.Chunk(2, 2)));
    assertTrue(result.contains(new Regions.Chunk(-3, 3)));
  }

  @Test
  void playerSafetyCannotBeDisabled() {
    assertEquals(
        24,
        Regions.eligible(new Regions.Rect(-3, -3, 3, 3), List.of(), 0, new Regions.Chunk(0, 0))
            .size());
  }

  @Test
  void limitsAreCheckedBeforeIteration() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Regions.eligible(
                new Regions.Rect(-100, 0, 100, 100), List.of(), 0, new Regions.Chunk(0, 0)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Regions.eligible(new Regions.Rect(0, 0, 1, 1), List.of(), 17, new Regions.Chunk(0, 0)));
  }

  @Test
  void exactly256Allowed() {
    assertEquals(
        256,
        Regions.eligible(new Regions.Rect(-8, -8, 7, 7), List.of(), 0, new Regions.Chunk(100, 100))
            .size());
  }

  @Test
  void boundsPreventOverflow() {
    assertThrows(
        IllegalArgumentException.class, () -> new Regions.Rect(Integer.MIN_VALUE, 0, 0, 1));
  }
}
