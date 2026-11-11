package dev.worldkeeper;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worldkeeper implements ClientModInitializer {
  public static final Logger LOG = LoggerFactory.getLogger("worldkeeper");
  static Project project;
  static Path source;
  static Regions.Chunk corner;
  static PreviewOperation operation;

  static boolean supported(MinecraftClient c) {
    return c.player != null
        && c.world != null
        && c.getServer() != null
        && !c.getServer().isRemote()
        && c.world.getRegistryKey().equals(World.OVERWORLD);
  }

  static void load(MinecraftClient c) throws Exception {
    Path path = c.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
    if (!path.equals(source) || project == null) {
      project = Project.load(path);
      source = path;
      corner = null;
    }
  }

  static void open(MinecraftClient c) {
    if (!supported(c)) {
      if (c.player != null)
        c.player.sendMessage(
            Text.literal("Worldkeeper needs a private singleplayer Overworld."), false);
      return;
    }
    try {
      load(c);
      c.setScreen(new KeeperScreen());
    } catch (Exception e) {
      c.player.sendMessage(Text.literal("Worldkeeper: " + e.getMessage()), false);
      LOG.error("Loading selections", e);
    }
  }

  @Override
  public void onInitializeClient() {
    KeyBinding open =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.worldkeeper.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.categories.worldkeeper"));
    KeyBinding mark =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.worldkeeper.corner",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "key.categories.worldkeeper"));
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          while (open.wasPressed()) open(c);
          while (mark.wasPressed())
            if (supported(c))
              try {
                load(c);
                var pos = c.player.getChunkPos();
                var current = new Regions.Chunk(pos.x, pos.z);
                if (corner == null) {
                  corner = current;
                  c.player.sendMessage(
                      Text.literal(
                          "Worldkeeper: first corner "
                              + pos.x
                              + ", "
                              + pos.z
                              + ". Move and press J again."),
                      true);
                } else {
                  project.selection =
                      Regions.Rect.corners(corner.x(), corner.z(), current.x(), current.z());
                  corner = null;
                  project.save(source);
                  c.player.sendMessage(
                      Text.literal("Worldkeeper: corners selected. Press K to protect or preview."),
                      true);
                }
              } catch (Exception e) {
                c.player.sendMessage(Text.literal("Worldkeeper: " + e.getMessage()), false);
              }
        });
    ScreenEvents.AFTER_INIT.register(
        (c, screen, w, h) -> {
          if (screen instanceof GameMenuScreen && supported(c))
            Screens.getButtons(screen)
                .add(
                    ButtonWidget.builder(Text.literal("Worldkeeper"), b -> open(c))
                        .dimensions(6, 6, 110, 20)
                        .build());
        });
    WorldRenderEvents.AFTER_ENTITIES.register(
        context -> {
          MinecraftClient c = MinecraftClient.getInstance();
          if (project == null
              || !supported(c)
              || !c.getServer()
                  .getSavePath(WorldSavePath.ROOT)
                  .toAbsolutePath()
                  .normalize()
                  .equals(source)) return;
          var matrices = context.matrixStack();
          var camera = context.camera().getPos();
          matrices.push();
          matrices.translate(-camera.x, -camera.y, -camera.z);
          var vertices = context.consumers().getBuffer(RenderLayer.getLines());
          draw(matrices, vertices, project.selection, 0, 1f, .45f, .1f);
          for (var p : project.protectedRegions) {
            draw(matrices, vertices, p.rect(), 0, .2f, 1f, .35f);
            if (project.buffer > 0)
              draw(matrices, vertices, p.rect(), project.buffer, .2f, .65f, 1f);
          }
          var p = c.player.getChunkPos();
          draw(matrices, vertices, new Regions.Rect(p.x, p.z, p.x, p.z), 2, .8f, .5f, 1f);
          matrices.pop();
        });
  }

  private static void draw(
      net.minecraft.client.util.math.MatrixStack matrices,
      net.minecraft.client.render.VertexConsumer vertices,
      Regions.Rect r,
      int b,
      float red,
      float green,
      float blue) {
    double y = MinecraftClient.getInstance().player.getY();
    WorldRenderer.drawBox(
        matrices,
        vertices,
        new Box(
            (r.minX() - b) * 16d,
            y - 1,
            (r.minZ() - b) * 16d,
            (r.maxX() + b + 1) * 16d,
            y + 3,
            (r.maxZ() + b + 1) * 16d),
        red,
        green,
        blue,
        1f);
  }
}
