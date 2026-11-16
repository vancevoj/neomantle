package slimeknights.mantle.client.screen.book.element;

import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import slimeknights.mantle.client.screen.book.BookScreen;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BookElement {

  /** TODO 1.21: make this field protected instead of public to ensure setter is used. */
  @Setter
  public BookScreen parent;

  protected Minecraft mc = Minecraft.getInstance();
  protected TextureManager renderEngine = this.mc.getTextureManager();

  public int x, y;

  public BookElement(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public abstract void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer);

  public void drawOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer) {
  }

  /**
   * Called when the mouse is clicked on the page containing this element
   * @param mouseX       Mouse X relative to the page
   * @param mouseY       Mouse Y relative to the page
   * @param mouseButton  Mouse button, using the legacy numbering: 0 is left, 1 is right, 2 is middle. See {@link BookScreen#legacyMouseButton(int)}.
   */
  public void mouseClicked(double mouseX, double mouseY, int mouseButton) {

  }

  /**
   * Called when the mouse is released on the page containing this element
   * @param mouseX              Mouse X relative to the page
   * @param mouseY              Mouse Y relative to the page
   * @param clickedMouseButton  Mouse button, using the legacy numbering: 0 is left, 1 is right, 2 is middle. See {@link BookScreen#legacyMouseButton(int)}.
   */
  public void mouseReleased(double mouseX, double mouseY, int clickedMouseButton) {

  }

  /**
   * Called when the mouse is dragged on the page containing this element. Positions are relative to the page.
   * @param button  Mouse button, using the legacy numbering: 0 is left, 1 is right, 2 is middle. See {@link BookScreen#legacyMouseButton(int)}.
   */
  public void mouseDragged(double clickX, double clickY, double mx, double my, double lastX, double lastY, int button) {

  }

  public void renderToolTip(GuiGraphicsExtractor graphics, Font fontRenderer, ItemStack stack, int x, int y) {
    List<Component> list = stack.getTooltipLines(Item.TooltipContext.of(this.mc.level), this.mc.player, this.mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);

    Font font = IClientItemExtensions.of(stack).getFont(stack, FontContext.TOOLTIP);
    if (font == null) {
      font = fontRenderer;
    }

    this.drawTooltip(graphics, list, x, y, font);
  }


  private static Stream<FormattedCharSequence> splitLine(FormattedText text, Font font, int maxWidth) {
    if (text instanceof Component component) {
      if (component.getString().isEmpty()) {
        return Stream.of(component.getVisualOrderText());
      }
    }
    return font.split(text, maxWidth).stream();
  }

  /** Tooltip positioner to keep the tooltip within the page for the relative mouse positions */
  private static final ClientTooltipPositioner POSITIONER = (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
    Vector2i pos = (new Vector2i(mouseX, mouseY)).add(12, -12);
    if (pos.x + tooltipWidth > BookScreen.PAGE_WIDTH) {
      pos.x = Math.max(pos.x - 24 - tooltipWidth, 4);
    }
    tooltipHeight += 3;
    if (pos.y + tooltipHeight > BookScreen.PAGE_HEIGHT) {
      pos.y = BookScreen.PAGE_HEIGHT - tooltipHeight;
    }
    return pos;
  };

  /**
   * Creates a positioner that positions the tooltip within the page using {@link #POSITIONER}, then moves it to screen coordinates.
   * Needed as tooltips are drawn at the end of the frame, after the page transform is gone.
   * @param pose  Page transform at the time the tooltip was requested
   */
  private static ClientTooltipPositioner pagePositioner(Matrix3x2fc pose) {
    Matrix3x2f transform = new Matrix3x2f(pose);
    return (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
      Vector2ic local = POSITIONER.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
      Vector2f screen = transform.transformPosition(local.x(), local.y(), new Vector2f());
      return new Vector2i(Math.round(screen.x), Math.round(screen.y));
    };
  }

  /**
   * Renders a tooltip in a book.
   * Based on {@link net.neoforged.neoforge.client.ClientHooks#gatherTooltipComponents(ItemStack, List, java.util.Optional, int, int, int, Font)}, but with three notable changes:
   * Uses the book page size (since mouseX and mouseY tend to be page relative), actually uses the updated tooltipX position, and drops the unused non-text component code.
   * The tooltip is drawn at the end of the frame on top of everything else, positioned using the current page transform.
   */
  public void drawTooltip(GuiGraphicsExtractor graphics, List<Component> textLines, int mouseX, int mouseY, Font font) {
    drawPageTooltip(graphics, textLines, mouseX, mouseY, font);
  }

  /**
   * Static version of {@link #drawTooltip(GuiGraphicsExtractor, List, int, int, Font)} for use outside of elements.
   * Mouse positions are relative to the page, using the current pose as the page transform.
   */
  public static void drawPageTooltip(GuiGraphicsExtractor graphics, List<Component> textLines, int mouseX, int mouseY, Font font) {
    // find max width of the tooltip
    int tooltipTextWidth = textLines.stream().mapToInt(font::width).max().orElse(0);
    boolean needsWrap = false;
    int tooltipX = mouseX + 12;
    // if the max width plus the position is too wide, need to fix
    if (tooltipX + tooltipTextWidth + 4 > BookScreen.PAGE_WIDTH) {
      // if repositioning fails, we will need to wrap the tooltip
      tooltipX = mouseX - 16 - tooltipTextWidth;
      if (tooltipX < 4) {
        if (mouseX > BookScreen.PAGE_WIDTH / 2) {
          tooltipTextWidth = mouseX - 12 - 8;
        } else {
          tooltipTextWidth = BookScreen.PAGE_WIDTH - 16 - mouseX;
        }

        needsWrap = true;
      }
    }

    // map to tooltip lines, wrapping if needed
    int tooltipTextWidthF = tooltipTextWidth;
    List<FormattedCharSequence> lines = needsWrap
      ? textLines.stream().flatMap(text -> splitLine(text, font, tooltipTextWidthF)).toList()
      : textLines.stream().map(Component::getVisualOrderText).toList();

    // render the tooltip, last tooltip requested in the frame wins as it is drawn on top
    // keep the gap after the first line that all multi line tooltips had on 1.21.1, 26.3 only adds it to item tooltips
    graphics.setTooltipForNextFrameInternal(font, lines.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()), mouseX, mouseY, pagePositioner(graphics.pose()), null, true, true);
  }

  /**
   * True if this element contains any text,
   * will not be included for HTML export
   */
  public boolean isText() {
    return false;
  }
}
