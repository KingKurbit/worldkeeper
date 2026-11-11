package dev.worldkeeper;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

public class KeeperScreen extends Screen {
  private TextFieldWidget x1, z1, x2, z2, name, buffer;
  private String message = "";
  private int page;

  public KeeperScreen() {
    super(Text.literal("Worldkeeper"));
  }

  private TextFieldWidget field(int x, int y, int w, String value, String label) {
    var f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(label));
    f.setMaxLength(80);
    f.setText(value);
    addDrawableChild(f);
    return f;
  }

  private void button(int x, int y, int w, String label, Runnable action) {
    addDrawableChild(
        ButtonWidget.builder(Text.literal(label), b -> action.run())
            .dimensions(x, y, w, 20)
            .build());
  }

  @Override
  protected void init() {
    int l = width / 2 - 155;
    var r = Worldkeeper.project.selection;
    x1 = field(l, 48, 70, "" + r.minX(), "First chunk X");
    z1 = field(l + 80, 48, 70, "" + r.minZ(), "First chunk Z");
    x2 = field(l + 160, 48, 70, "" + r.maxX(), "Second chunk X");
    z2 = field(l + 240, 48, 70, "" + r.maxZ(), "Second chunk Z");
    name = field(l, 76, 150, "Protected build", "Region name");
    buffer = field(l + 240, 76, 70, "" + Worldkeeper.project.buffer, "Buffer in chunks");
    button(l, 102, 98, "Set regeneration", () -> apply(false));
    button(l + 104, 102, 98, "Add protection", () -> apply(true));
    button(l + 208, 102, 102, "Save buffer", () -> applyBuffer());
    int count = Worldkeeper.project.protectedRegions.size();
    page = Math.max(0, Math.min(page, Math.max(0, (count - 1) / 2)));
    for (int i = page * 2; i < Math.min(count, page * 2 + 2); i++) {
      final int index = i;
      var p = Worldkeeper.project.protectedRegions.get(i);
      int y = 132 + (i - page * 2) * 22;
      button(
          l,
          y,
          198,
          p.name(),
          () -> {
            name.setText(p.name());
            x1.setText("" + p.rect().minX());
            z1.setText("" + p.rect().minZ());
            x2.setText("" + p.rect().maxX());
            z2.setText("" + p.rect().maxZ());
          });
      button(
          l + 202,
          y,
          54,
          "Rename",
          () -> {
            if (!name.getText().isBlank()) {
              Worldkeeper.project.protectedRegions.set(
                  index, new Regions.Protected(name.getText().strip(), p.rect()));
              save();
              clearAndInit();
            }
          });
      button(
          l + 260,
          y,
          50,
          "Delete",
          () -> {
            Worldkeeper.project.protectedRegions.remove(index);
            save();
            clearAndInit();
          });
    }
    button(
        l,
        176,
        24,
        "<",
        () -> {
          page--;
          clearAndInit();
        });
    button(
        l + 286,
        176,
        24,
        ">",
        () -> {
          page++;
          clearAndInit();
        });
    button(l, height - 34, 200, "Create Preview World", this::confirm);
    button(l + 210, height - 34, 100, "Back", this::close);
  }

  private void applyBuffer() {
    try {
      int b = Integer.parseInt(buffer.getText());
      if (b < 0 || b > 16) throw new IllegalArgumentException("Buffer: 0–16");
      Worldkeeper.project.buffer = b;
      save();
    } catch (Exception e) {
      message = e.getMessage();
    }
  }

  private void apply(boolean protect) {
    try {
      var r =
          Regions.Rect.corners(
              Integer.parseInt(x1.getText()),
              Integer.parseInt(z1.getText()),
              Integer.parseInt(x2.getText()),
              Integer.parseInt(z2.getText()));
      if (protect) {
        if (Worldkeeper.project.protectedRegions.size() >= 128)
          throw new IllegalArgumentException("Maximum 128 protected regions");
        if (name.getText().isBlank()) throw new IllegalArgumentException("Enter a name");
        Worldkeeper.project.protectedRegions.add(new Regions.Protected(name.getText().strip(), r));
      } else {
        if (r.area() > Regions.LIMIT) throw new IllegalArgumentException("Maximum 256 chunks");
        Worldkeeper.project.selection = r;
      }
      save();
      clearAndInit();
    } catch (Exception e) {
      message = "Invalid selection: " + e.getMessage();
    }
  }

  private void save() {
    try {
      Worldkeeper.project.save(Worldkeeper.source);
      message = "Selections saved";
    } catch (Exception e) {
      message = "Save failed: " + e.getMessage();
    }
  }

  private Regions.Chunk playerChunk() {
    var p = client.player.getChunkPos();
    return new Regions.Chunk(p.x, p.z);
  }

  private void confirm() {
    try {
      if (Worldkeeper.operation != null && !Worldkeeper.operation.done)
        throw new IllegalStateException("A preview is already running");
      var p = Worldkeeper.project;
      if (Regions.eligible(p.selection, p.protectedRegions, p.buffer, playerChunk()).isEmpty())
        throw new IllegalArgumentException(
            "No eligible chunks; move the selection away from protected areas");
      p.save(Worldkeeper.source);
      client.setScreen(
          new ConfirmScreen(
              yes -> {
                if (yes) start();
                else client.setScreen(this);
              },
              Text.literal("Save, close, and create preview?"),
              Text.literal(
                  "Your source stays available. The whole save is copied. Selected chunks"
                      + " regenerate when you open the preview. New content depends on your seed,"
                      + " settings and installed mods. Terrain seams may remain."),
              Text.literal("Save & create"),
              Text.literal("Back")));
    } catch (Exception e) {
      message = e.getMessage();
    }
  }

  private void start() {
    var server = client.getServer();
    var uuid = client.player.getUuid();
    var p = Worldkeeper.project;
    var selection = p.selection;
    var protection = List.copyOf(p.protectedRegions);
    int b = p.buffer;
    // Capture authoritative position on the server, not a possibly stale client position.
    server.execute(
        () -> {
          var player = server.getPlayerManager().getPlayer(uuid);
          if (player == null) {
            client.execute(
                () -> {
                  message = "Player disconnected";
                  client.setScreen(this);
                });
            return;
          }
          var pos = player.getChunkPos();
          var eligible =
              Regions.eligible(selection, protection, b, new Regions.Chunk(pos.x, pos.z));
          client.execute(
              () -> {
                var op = new PreviewOperation();
                Worldkeeper.operation = op;
                var source = Worldkeeper.source;
                var game = client.runDirectory.toPath().toAbsolutePath();
                client.world.disconnect();
                client
                    .disconnect(); // Vanilla disconnect waits for integrated-server shutdown and
                                   // storage close.
                client.setScreen(new ProgressScreen(op));
                Thread worker =
                    new Thread(() -> op.run(game, source, eligible), "Worldkeeper preview");
                worker.setDaemon(true);
                worker.start();
              });
        });
  }

  @Override
  public void render(DrawContext c, int mx, int my, float delta) {
    super.render(c, mx, my, delta);
    int l = width / 2 - 155;
    c.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xffffff);
    c.drawTextWithShadow(
        textRenderer, "Chunk coordinates: first X / Z     second X / Z", l, 34, 0xbbbbbb);
    c.drawTextWithShadow(textRenderer, "Buffer:", l + 188, 82, 0xbbbbbb);
    c.drawTextWithShadow(
        textRenderer,
        "Protection list: click name to inspect; page " + (page + 1),
        l,
        122,
        0x66ff88);
    try {
      var p = Worldkeeper.project;
      int n = Regions.eligible(p.selection, p.protectedRegions, p.buffer, playerChunk()).size();
      int protectedCount = 0;
      for (int x = p.selection.minX(); x <= p.selection.maxX(); x++)
        for (int z = p.selection.minZ(); z <= p.selection.maxZ(); z++) {
          var chunk = new Regions.Chunk(x, z);
          if (p.protectedRegions.stream().anyMatch(r -> r.rect().contains(chunk, p.buffer)))
            protectedCount++;
        }
      c.drawCenteredTextWithShadow(
          textRenderer,
          "Protected "
              + protectedCount
              + " | excluded "
              + (p.selection.area() - n)
              + " | regen "
              + n,
          width / 2,
          178,
          0xffffff);
      c.drawCenteredTextWithShadow(
          textRenderer, "Includes player safety: 5 x 5 chunks", width / 2, 190, 0xbbbbbb);
    } catch (Exception e) {
      c.drawCenteredTextWithShadow(
          textRenderer, "Selection exceeds 256 chunks", width / 2, 178, 0xff7777);
    }
    c.drawCenteredTextWithShadow(
        textRenderer, Text.literal(message), width / 2, height - 11, 0xffdd88);
  }

  private static class ProgressScreen extends Screen {
    final PreviewOperation op;

    ProgressScreen(PreviewOperation op) {
      super(Text.literal("Worldkeeper preview"));
      this.op = op;
    }

    @Override
    protected void init() {
      addDrawableChild(
          ButtonWidget.builder(
                  Text.literal("Cancel / Back"),
                  b -> {
                    if (op.done) client.setScreen(new SelectWorldScreen(new TitleScreen()));
                    else op.cancel.set(true);
                  })
              .dimensions(width / 2 - 100, height - 45, 200, 20)
              .build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
      return false;
    }

    @Override
    public void render(DrawContext c, int x, int y, float d) {
      super.render(c, x, y, d);
      c.drawCenteredTextWithShadow(textRenderer, title, width / 2, 30, 0xffffff);
      c.drawTextWrapped(textRenderer, Text.literal(op.status), 20, 65, width - 40, 0xffffff);
      c.drawTextWrapped(
          textRenderer,
          Text.literal(
              op.done
                  ? "Use Back to open the world list. Your source remains available. Failed or"
                        + " cancelled staging is retained outside saves."
                  : "Copying off-thread. Cancel is checked between copy blocks and chunk"
                        + " removals."),
          20,
          110,
          width - 40,
          0xbbbbbb);
    }
  }
}
