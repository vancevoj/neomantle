package slimeknights.mantle.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.config.Config;

import java.util.Random;

public class ExtraHeartRenderHandler {
  private static final ResourceLocation ICON_HEARTS = new ResourceLocation(Mantle.modId, "textures/gui/extra_hearts.png");
  private static final ResourceLocation ICON_VANILLA = Gui.GUI_ICONS_LOCATION;
  private static final int ROW_HEIGHT = 10;
  /** Offsets for each heart position */
  private final int[] offsets = new int[20];

  private final Minecraft mc = Minecraft.getInstance();

  /** Health in the last update */
  private int lastHealth = 0;
  /** Current health to display */
  private int displayHealth = 0;
  /** Duration the hearts will blink */
  private long healthBlinkTime = 0;
  /** Last time health was updated */
  private long lastHealthTime = 0;
  private final Random rand = new Random();

  /* HUD */

  /**
   * Event listener
   * @param event  Event instance
   */
  @SubscribeEvent(priority = EventPriority.LOW)
  public void renderHealthbar(RenderGuiOverlayEvent.Pre event) {
    if (event.isCanceled() || !Config.EXTRA_HEART_RENDERER.get() || event.getOverlay() != VanillaGuiOverlay.PLAYER_HEALTH.type()) {
      return;
    }
    // ensure its visible
    if (!(mc.gui instanceof ForgeGui gui) || mc.options.hideGui || !gui.shouldDrawSurvivalElements()) {
      return;
    }
    Entity renderViewEnity = this.mc.getCameraEntity();
    if (!(renderViewEnity instanceof Player player)) {
      return;
    }
    gui.setupOverlayRenderState(true, false);

    this.mc.getProfiler().push("health");

    // based on the top of Gui#renderPlayerHealth
    int tickCount = this.mc.gui.getGuiTicks();
    int health = Mth.ceil(player.getHealth());
    boolean highlight = this.healthBlinkTime > tickCount && (this.healthBlinkTime - tickCount) / 3L % 2L == 1L;

    long systemTime = Util.getMillis();
    if (player.invulnerableTime > 0) {
      if (health < this.lastHealth) {
        this.lastHealthTime = systemTime;
        this.healthBlinkTime = tickCount + 20;
      } else if (health > this.lastHealth) {
        this.lastHealthTime = systemTime;
        this.healthBlinkTime = tickCount + 10;
      }
    }
    if (systemTime - this.lastHealthTime > 1000L) {
      this.displayHealth = health;
      this.lastHealthTime = systemTime;
    }

    this.lastHealth = health;
    int displayHealth = this.displayHealth;
    this.rand.setSeed(tickCount * 312871L);

    // setup window size
    Window window = this.mc.getWindow();
    int left = window.getGuiScaledWidth() / 2 - 91;
    int top = window.getGuiScaledHeight() - gui.leftHeight;

    // grab max health as the max of it or the health we will display
    // cap it to 20, as this just determines heart count
    int showHealth = Math.min(Math.max(Mth.ceil(player.getAttributeValue(Attributes.MAX_HEALTH)), Math.max(displayHealth, health)), 20);
    int absorb = Mth.ceil(player.getAbsorptionAmount());

    // make hearts bounce with regen
    int regen = -1;
    if (player.hasEffect(MobEffects.REGENERATION)) {
      // vanilla uses max health here, but between us capping it and it looking horrible at low sizes, use 25
      regen = tickCount % 25;
    }
    // end code based on Gui#renderPlayerHealth

    // determine which hearts to display based on status effects
    // this is inspired by Gui.HeartType#forPlayer, keep it in sync
    int container = 0;
    int heartOffset = 0;
    int absorpOffset = 216;
    if (player.hasEffect(MobEffects.POISON)) {
      heartOffset = 36;
    } else if (player.hasEffect(MobEffects.WITHER)) {
      heartOffset = 72;
      // absorption shows as wither under the effects of wither, no other changes
      absorpOffset = 72;
    } else if (player.isFullyFrozen()) {
      heartOffset = 108;
    }
    // if hardcore, switch to the hardcore hearts
    assert this.mc.level != null;
    if (this.mc.level.getLevelData().isHardcore()) {
      heartOffset += 9;
      container += 9;
      heartOffset += 9;
    }

    // if health is low, the hearts will wiggle
    boolean wiggle = (health + absorb <= 4);

    // number of heart backgrounds to draw
    int showHearts = (showHealth + 1) / 2;
    // if we have less than a row of hearts, and at most 1 row of absorption,
    boolean compactAbsorption = showHearts < 10 && absorb <= 2 * (10 - showHearts);

    // time to draw heart backgrounds
    GuiGraphics graphics = event.getGuiGraphics();

    // render containers for health
    renderContainerRow(graphics, left, top, container, 0, showHealth / 2, showHealth < 20 && showHealth % 2 == 1, highlight, wiggle, regen);
    // for absorption, render containers in same row if they fit, but never split across rows (that gets confusing when we start stacking)
    if (absorb > 0) {
      boolean half = absorb < 20 && absorb % 2 == 1;
      int absorbHearts = absorb / 2;
      if (compactAbsorption) {
        renderContainerRow(graphics, left + 8 * showHearts, top, container, 10, absorbHearts, half, highlight, wiggle, -1);
      } else {
        renderContainerRow(graphics, left, top - ROW_HEIGHT, container, 10, Math.min(absorbHearts, 10), half, highlight, wiggle, -1);
      }
    }

    // render player health
    if (highlight && displayHealth > health) {
      renderHeartsWithDamage(graphics, left, top, heartOffset, health, displayHealth);
    } else {
      renderHearts(graphics, left, top, heartOffset, health, 0);
    }


    // render absorption
    // if we have less than 10 hearts, put absorption in the same row if it fits
    if (compactAbsorption) {
      int absorbHearts = absorb / 2;
      // render the top color on both rows
      renderHeartRow(graphics, left + showHearts * 8, top, absorpOffset, 10, 0, 0, absorbHearts, absorb % 2 == 1);
    } else {
      renderHearts(graphics, left, top - ROW_HEIGHT, absorpOffset, absorb, 10);
    }

    // prepare the GUI for the event
    RenderSystem.setShaderTexture(0, ICON_VANILLA);
    gui.leftHeight += ROW_HEIGHT;
    if (absorb > 0) {
      gui.leftHeight += ROW_HEIGHT;
    }

    event.setCanceled(true);
    RenderSystem.disableBlend();
    this.mc.getProfiler().pop();
    //noinspection UnstableApiUsage  I do what I want (more accurately, we override the renderer but want to let others still respond in post)
    MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Post(event.getWindow(), graphics, event.getPartialTick(), VanillaGuiOverlay.PLAYER_HEALTH.type()));
  }

  /**
   * Shared logic to render custom hearts
   *
   * @param graphics     Graphics instance
   * @param x            Health bar top corner
   * @param y            Health bar top corner
   * @param heartOffset  Offset for heart style
   * @param count        Number to render
   * @param indexOffset  Heart to raise for regen
   */
  private void renderHearts(GuiGraphics graphics, int x, int y, int heartOffset, int count, int indexOffset) {
    int heartsTopColor = (count % 20) / 2;
    int heartIndex = count / 20;
    // if we have 1 full non-vanilla row, render the right side hearts
    if (count >= 20) {
      renderHeartRow(graphics, x, y, heartOffset, indexOffset, heartIndex - 1, heartsTopColor, 10, false);
    }
    // for the current row, need to render starting from the left
    renderHeartRow(graphics, x, y, heartOffset, indexOffset, heartIndex, 0, heartsTopColor, count % 2 == 1);
  }

  /**
   * Shared logic to render custom hearts with a last damage
   *
   * @param graphics    Graphics instance
   * @param x           Health bar top corner
   * @param y           Health bar top corner
   * @param heartOffset Offset for heart style
   * @param current     Current to render
   * @param last        Number previous tick
   */
  private void renderHeartsWithDamage(GuiGraphics graphics, int x, int y, int heartOffset, int current, int last) {
    int currentTopRow = current % 20;
    int currentRight = currentTopRow / 2;
    int lastTopRow = last % 20;
    int lastRight = lastTopRow / 2;

    // determine how to render the last damage
    int damageTaken = last - current;
    boolean bigDamage = damageTaken >= 20;
    boolean damageWrapped = bigDamage || lastRight < currentRight;

    // damage taken from middle to right
    if (damageWrapped) {
      // this implicately checks that last >= 20
      // damage wrapping around means we cover up current middle to right, just a question of whether we cover current entirely
      // ???__
      //    ^^
      renderHeartRow(graphics, x, y, heartOffset + 18, 0, last / 20 - 1, bigDamage ? lastRight : currentRight, 10, false);
    } else {
      // current health from middle to right
      if (current >= 20) {
        // *--##
        //    ^^
        renderHeartRow(graphics, x, y, heartOffset, 0, current / 20 - 1, lastRight, 10, false);
      }

      // damage taken did not wrap around, render it on top of lower current health bar (rendering lower health if present)
      // *--##
      //  ^^
      renderHeartRow(graphics, x, y, heartOffset + 18, 0, last / 20, currentRight, lastRight, lastTopRow % 2 == 1);
    }

    // current health from left to middle
    if (!bigDamage) {
      // ***?? OR --*??
      // ^^^        ^
      renderHeartRow(graphics, x, y, heartOffset, 0, current / 20, damageWrapped ? lastRight : 0, currentRight, currentTopRow % 2 == 1);
    }

    // damage taken from left to middle
    if (damageWrapped) {
      // --???
      // ^^
      renderHeartRow(graphics, x, y, heartOffset + 18, 0, last / 20, 0, lastRight, lastTopRow % 2 == 1);
    }
  }

  /**
   * Renders a row of hearts
   * @param graphics     Graphics instance
   * @param x            X position to draw
   * @param y            Y position to draw
   * @param typeOffset   Heart type to render
   * @param indexOffset  Offset for index in {@link #offsets}
   * @param heartIndex   Heart color to render
   * @param start        First heart to render
   * @param end          Above the last heart to renderer
   * @param half         If true, renders an extra half heart
   */
  private void renderHeartRow(GuiGraphics graphics, int x, int y, int typeOffset, int indexOffset, int heartIndex, int start, int end, boolean half) {
    heartIndex %= 12;
    // draw full hearts
    for (int i = start; i < end; i += 1) {
      graphics.blit(ICON_HEARTS, x + 8 * i, y + offsets[i + indexOffset], 18 * heartIndex, typeOffset, 9, 9);
    }
    // draw half heart
    if (half) {
      graphics.blit(ICON_HEARTS, x + 8 * end, y + offsets[end + indexOffset], 9 + 18 * heartIndex, typeOffset, 9, 9);
    }
  }

  /**
   * Renders a row of heart containers
   * @param graphics     Graphics instance
   * @param x            X position to draw
   * @param y            Y position to draw
   * @param typeOffset   Container type to render
   * @param indexOffset  Offset for index in {@link #offsets}
   * @param end          Above the last heart index to render
   * @param half         If true, renders an extra half container
   * @param highlight    If true, highlights the heart for changing health
   * @param wiggle       If true, health is low so the hearts wiggle
   * @param regen        Index of the heart for the regen bounce
   */
  private void renderContainerRow(GuiGraphics graphics, int x, int y, int typeOffset, int indexOffset, int end, boolean half, boolean highlight, boolean wiggle, int regen) {
    for (int i = 0; i < end; i++) {
      // figure out the offset for this heart, and store it for future renderers to check
      int offset = 0;
      if (wiggle) {
        offset = this.rand.nextInt(2);
      }
      if (i == regen) {
        offset -= 2;
      }
      offsets[i + indexOffset] = offset;

      int localX = x + i * 8;
      int localY = y + offset;
      if (highlight) {
        graphics.blit(ICON_HEARTS, localX, localY, 247, typeOffset + 18, 9, 9);
      } else {
        graphics.blit(ICON_HEARTS, localX, localY, 247, typeOffset, 9, 9);
      }
    }
    // draw extra half heart container
    if (half) {
      int offset = 0;
      if (wiggle) {
        offset = this.rand.nextInt(2);
      }
      if (end == regen) {
        offset -= 2;
      }
      offsets[end + indexOffset] = offset;
      // if health is changing, add the white border
      int localX = x + end * 8;
      int localY = y + offset;
      if (highlight) {
        graphics.blit(ICON_HEARTS, localX, localY, 247, typeOffset + 18 + 36, 9, 9);
      } else {
        graphics.blit(ICON_HEARTS, localX, localY, 247, typeOffset + 36, 9, 9);
      }
    }
  }
}
