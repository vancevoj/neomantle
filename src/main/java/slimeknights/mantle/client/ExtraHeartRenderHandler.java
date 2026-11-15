package slimeknights.mantle.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.config.Config;
import slimeknights.mantle.config.Config.HeartRenderer;

import java.util.Random;

/**
 * Replacement for the vanilla {@code PLAYER_HEALTH} GUI layer, registered via {@link #register(RegisterGuiLayersEvent)}.
 * In 1.21 the old {@code RenderGuiOverlayEvent} cancel-and-redraw flow became a layer replacement.
 * This is a HUD layer, it must never be registered on the event bus.
 */
public class ExtraHeartRenderHandler implements GuiLayer {
  private static final Identifier ICON_HEARTS = Identifier.fromNamespaceAndPath(Mantle.modId, "textures/gui/extra_hearts.png");
  /** Width and height of {@link #ICON_HEARTS} */
  private static final int TEXTURE_SIZE = 256;
  /** Number of heart color variants */
  private static final int HEART_VARIANTS = 12;
  /** Number of heart color variants */
  private static final int HEART_SIZE = 9;
  /** Offset between hearts in the renderer */
  private static final int HEART_OFFSET = HEART_SIZE - 1;
  /** Height of a row of hearts */
  private static final int ROW_HEIGHT = HEART_SIZE + 1;

  /** Offset from a heart variant to the hardcore hearts */
  private static final int HARDCORE_OFFSET = HEART_SIZE;
  /** Offset from a heart variant to the last damage hearts */
  private static final int DAMAGE_OFFSET = 2 * HEART_SIZE;

  /** Starting pixel value for the normal hearts */
  private static final int NORMAL_VARIANT = 0;
  /** Starting pixel value for the poison hearts */
  private static final int POISON_VARIANT = 36;
  /** Starting pixel value for the wither hearts */
  private static final int WITHER_VARIANT = 72;
  /** Starting pixel value for the freeze hearts */
  private static final int FREEZE_VARIANT = 108;
  /** Starting pixel value for the absorption hearts */
  private static final int ABSORPTION_VARIANT = 216;
  /** Extra background "containers" for showing max health */
  private static final int MAX_VARIANT = 180;

  /** Background container for normal hearts */
  private static final int NORMAL_CONTAINER = 216;
  /** Background container for absorption hearts */
  private static final int ABSORPTION_CONTAINER = 234;

  private final Minecraft mc = Minecraft.getInstance();
  /** Vanilla health layer, drawn instead of the custom hearts when the renderer is disabled in the config */
  private final GuiLayer vanillaLayer;
  /** Offsets for each heart position */
  private final int[] offsets = new int[20];

  /** Health in the last update */
  private int lastHealth = 0;
  /** Current health to display */
  private int displayHealth = 0;
  /** Duration the hearts will blink */
  private long healthBlinkTime = 0;
  /** Last time health was updated */
  private long lastHealthTime = 0;
  private final Random rand = new Random();

  /**
   * Creates a new heart renderer
   * @param vanillaLayer  Vanilla health layer, used when the renderer is disabled in the config
   */
  public ExtraHeartRenderHandler(GuiLayer vanillaLayer) {
    this.vanillaLayer = vanillaLayer;
  }

  /**
   * Registers the heart renderer in place of the vanilla {@code PLAYER_HEALTH} GUI layer.
   * Call from a {@link RegisterGuiLayersEvent} listener on the mod bus.
   * The vanilla layer is wrapped rather than discarded so it still draws when the config disables this renderer.
   */
  public static void register(RegisterGuiLayersEvent event) {
    event.wrapLayer(VanillaGuiLayers.PLAYER_HEALTH, ExtraHeartRenderHandler::new);
  }

  /* HUD */

  /**
   * Renders the custom health bar as a replacement for the vanilla {@code PLAYER_HEALTH} GUI layer.
   * @param graphics      Graphics instance
   * @param deltaTracker  Frame delta tracker
   */
  @Override
  public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    HeartRenderer renderer = Config.HEART_RENDERER.get();
    if (renderer == HeartRenderer.DISABLE) {
      // the wrapped vanilla layer does its own visibility checks
      this.vanillaLayer.render(graphics, deltaTracker);
      return;
    }
    // ensure its visible: hud shown, survival-style gamemode, and a player camera (mirrors vanilla's survivalVisible check)
    if (this.mc.gui.hud.isHidden() || this.mc.gameMode == null || !this.mc.gameMode.canHurtPlayer()) {
      return;
    }
    Entity renderViewEnity = this.mc.getCameraEntity();
    if (!(renderViewEnity instanceof Player player)) {
      return;
    }

    Profiler.get().push("health");

    // based on the top of Hud#extractHealthLevel
    int tickCount = this.mc.gui.hud.getGuiTicks();
    int health = Mth.ceil(player.getHealth());
    boolean highlight = this.healthBlinkTime > tickCount && (this.healthBlinkTime - tickCount) / 3L % 2L == 1L;

    long systemTime = Util.getMillis();
    // 26.3: the damage cooldown moved from Entity#invulnerableTime to LivingEntity#damageCooldownTime, matching Hud#extractHealthLevel
    if (player.damageCooldownTime > 0) {
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
    int left = graphics.guiWidth() / 2 - 91;
    int top = graphics.guiHeight() - this.mc.gui.hud.leftHeight;

    // grab max health as the max of it or the health we will display
    // cap it to 20, as this just determines heart count
    int maxHealth = Math.max(Mth.ceil(player.getAttributeValue(Attributes.MAX_HEALTH)), Math.max(displayHealth, health));
    int absorb = Mth.ceil(player.getAbsorptionAmount());

    // make hearts bounce with regen
    int regen = -1;
    if (player.hasEffect(MobEffects.REGENERATION)) {
      // vanilla uses max health here, but between us capping it and it looking horrible at low sizes, use 25
      regen = tickCount % 25;
    }
    // end code based on Hud#extractHealthLevel

    // determine which hearts to display based on status effects
    // this is inspired by Hud.HeartType#forPlayer, keep it in sync
    int container = MAX_VARIANT;
    int heartOffset = NORMAL_VARIANT;
    int absorpOffset = ABSORPTION_VARIANT;
    if (player.hasEffect(MobEffects.POISON)) {
      heartOffset = POISON_VARIANT;
    } else if (player.hasEffect(MobEffects.WITHER)) {
      heartOffset = WITHER_VARIANT;
      // absorption shows as wither under the effects of wither, no other changes
      absorpOffset = WITHER_VARIANT;
    } else if (player.isFullyFrozen()) {
      heartOffset = FREEZE_VARIANT;
    }
    // if hardcore, switch to the hardcore hearts
    assert this.mc.level != null;
    if (this.mc.level.getLevelData().isHardcore()) {
      heartOffset += HARDCORE_OFFSET;
      absorpOffset += HARDCORE_OFFSET;
      container += HARDCORE_OFFSET;
    }
    if (highlight) {
      container += DAMAGE_OFFSET;
    }

    // if health is low, the hearts will wiggle
    boolean wiggle = (health + absorb <= 4);

    // number of heart backgrounds to draw
    int showHealth = Math.min(maxHealth, 20);
    int showHearts = (showHealth + 1) / 2;
    // if we have less than a row of hearts, and at most 1 row of absorption,
    boolean compactAbsorption = showHearts < 10 && absorb <= 2 * (10 - showHearts);

    // time to draw heart backgrounds (graphics is the layer render target)

    // render max health backgrounds
    int absorptionOffset = ROW_HEIGHT;
    setOffsets(0, showHearts, wiggle, regen);
    // if we have a large enough amount of health and its enabled, render the max health as backgrounds
    if (renderer == HeartRenderer.NO_MAX || maxHealth <= 20) {
      renderHeartRow(graphics, left, top, 0, NORMAL_CONTAINER, container, 0, showHealth / 2, showHealth % 2 == 1);
    } else {
      absorptionOffset += 1;
      // all hearts for the container row are offset to the left by 1 for the sake of doing -20 here. Means you don't see red until row 13
      renderHearts(graphics, left, top - 2, container, maxHealth - 20, 0);
      renderHeartRow(graphics, left, top, 0, NORMAL_CONTAINER, container, 0, 10, false);
    }

    // render containers for health
    // for absorption, render containers in same row if they fit, but never split across rows (that gets confusing when we start stacking)
    if (absorb > 0) {
      setOffsets(10, Math.min((absorb - 1) / 2, 10), wiggle, -1);
      boolean half = absorb < 20 && absorb % 2 == 1;
      int absorbHearts = absorb / 2;
      if (compactAbsorption) {
        renderHeartRow(graphics, left + 8 * showHearts, top, 10, ABSORPTION_CONTAINER, container, 0, absorbHearts, half);
      } else {
        renderHeartRow(graphics, left, top - absorptionOffset, 10, ABSORPTION_CONTAINER, container, 0, Math.min(absorbHearts, 10), half);
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
      renderHeartRow(graphics, left + showHearts * 8, top, 10, 0, absorpOffset, 0, absorbHearts, absorb % 2 == 1);
    } else {
      renderHearts(graphics, left, top - absorptionOffset, absorpOffset, absorb, 10);
    }

    // advance the shared HUD offset so layers drawn after us (food, armor, air) stack above the hearts,
    // matching what vanilla's extractHealthLevel does. GuiGraphicsExtractor.blit carries its own texture and pipeline, so no
    // render state is needed, and because this layer replaces PLAYER_HEALTH there is no event to cancel/repost.
    this.mc.gui.hud.leftHeight += ROW_HEIGHT;
    if (!compactAbsorption && absorb > 0) {
      this.mc.gui.hud.leftHeight += absorptionOffset;
    }

    Profiler.get().pop();
  }

  /** Computes the color U offset for a given heart index */
  private static int colorOffset(int heartIndex) {
    return (heartIndex % HEART_VARIANTS) * 2 * HEART_SIZE;
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
  private void renderHearts(GuiGraphicsExtractor graphics, int x, int y, int heartOffset, int count, int indexOffset) {
    int heartsTopColor = (count % 20) / 2;
    int heartIndex = count / 20;
    // if we have 1 full non-vanilla row, render the right side hearts
    if (count >= 20) {
      renderHeartRow(graphics, x, y, indexOffset, colorOffset(heartIndex - 1), heartOffset, heartsTopColor, 10, false);
    }
    // for the current row, need to render starting from the left
    renderHeartRow(graphics, x, y, indexOffset, colorOffset(heartIndex), heartOffset, 0, heartsTopColor, count % 2 == 1);
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
  private void renderHeartsWithDamage(GuiGraphicsExtractor graphics, int x, int y, int heartOffset, int current, int last) {
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
      renderHeartRow(graphics, x, y, 0, colorOffset(last / 20 - 1), heartOffset + DAMAGE_OFFSET, bigDamage ? lastRight : currentRight, 10, false);
    } else {
      // current health from middle to right
      if (current >= 20) {
        // *--##
        //    ^^
        renderHeartRow(graphics, x, y, 0, colorOffset(current / 20 - 1), heartOffset, lastRight, 10, false);
      }

      // damage taken did not wrap around, render it on top of lower current health bar (rendering lower health if present)
      // *--##
      //  ^^
      renderHeartRow(graphics, x, y, 0, colorOffset(last / 20), heartOffset + DAMAGE_OFFSET, currentRight, lastRight, lastTopRow % 2 == 1);
    }

    // current health from left to middle
    if (!bigDamage) {
      // ***?? OR --*??
      // ^^^        ^
      renderHeartRow(graphics, x, y, 0, colorOffset(current / 20), heartOffset, damageWrapped ? lastRight : 0, currentRight, currentTopRow % 2 == 1);
    }

    // damage taken from left to middle
    if (damageWrapped) {
      // --???
      // ^^
      renderHeartRow(graphics, x, y, 0, colorOffset(last / 20), heartOffset + DAMAGE_OFFSET, 0, lastRight, lastTopRow % 2 == 1);
    }
  }

  /**
   * Renders a row of hearts
   *
   * @param graphics    Graphics instance
   * @param x           X position to draw
   * @param y           Y position to draw
   * @param indexOffset Offset for index in {@link #offsets}
   * @param uOffset     Horizontal offset, typically heart color
   * @param vOffset     Vertical offset, typically heart variant
   * @param start       First heart to render
   * @param end         Above the last heart to renderer
   * @param half        If true, renders an extra half heart
   */
  private void renderHeartRow(GuiGraphicsExtractor graphics, int x, int y, int indexOffset, int uOffset, int vOffset, int start, int end, boolean half) {
    // draw full hearts
    for (int i = start; i < end; i += 1) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_HEARTS, x + HEART_OFFSET * i, y + offsets[i + indexOffset], uOffset, vOffset, HEART_SIZE, HEART_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
    }
    // draw half heart
    if (half) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_HEARTS, x + HEART_OFFSET * end, y + offsets[end + indexOffset], uOffset + HEART_SIZE, vOffset, HEART_SIZE, HEART_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
    }
  }

  /**
   * Renders a row of heart containers
   * @param indexOffset  Offset for index in {@link #offsets}
   * @param end          Above the last heart index to render
   * @param wiggle       If true, health is low so the hearts wiggle
   * @param regen        Index of the heart for the regen bounce
   */
  private void setOffsets(int indexOffset, int end, boolean wiggle, int regen) {
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
    }
  }
}
