package slimeknights.mantle.client.screen;

import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix3x2fStack;
import slimeknights.mantle.inventory.MultiModuleContainerMenu;
import slimeknights.mantle.inventory.WrapperSlot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MultiModuleScreen<CONTAINER extends MultiModuleContainerMenu<?>> extends AbstractContainerScreen<CONTAINER> {

  protected List<ModuleScreen<?,?>> modules = Lists.newArrayList();

  public int cornerX;
  public int cornerY;
  public int realWidth;
  public int realHeight;

  public MultiModuleScreen(CONTAINER container, Inventory playerInventory, Component title) {
    this(container, playerInventory, title, DEFAULT_IMAGE_WIDTH, DEFAULT_IMAGE_HEIGHT);
  }

  /**
   * Creates a screen with the given main panel size. The size may still be changed in the constructor of subclasses
   * (imageWidth and imageHeight are widened by the access transformer), as modules resize the screen in {@link #init()}.
   */
  public MultiModuleScreen(CONTAINER container, Inventory playerInventory, Component title, int imageWidth, int imageHeight) {
    super(container, playerInventory, title, imageWidth, imageHeight);

    this.realWidth = -1;
    this.realHeight = -1;
  }

  protected void addModule(ModuleScreen<?,?> module) {
    this.modules.add(module);
  }

  public List<Rect2i> getModuleAreas() {
    List<Rect2i> areas = new ArrayList<>(this.modules.size());
    for (ModuleScreen<?,?> module : this.modules) {
      areas.add(module.getArea());
    }
    return areas;
  }

  @Override
  protected void init() {
    if (this.realWidth > -1) {
      // has to be reset before calling initGui so the position is getting retained
      this.imageWidth = this.realWidth;
      this.imageHeight = this.realHeight;
    }

    super.init();

    this.cornerX = this.leftPos;
    this.cornerY = this.topPos;
    this.realWidth = this.imageWidth;
    this.realHeight = this.imageHeight;

    for (ModuleScreen<?,?> module : this.modules) {
      this.updateSubmodule(module);
    }
    // TODO: this is a small ordering change, does it need another hook?
    for (ModuleScreen<?,?> module : this.modules) {
      module.init(this.width, this.height);
      this.updateSubmodule(module);
    }
  }

  /**
   * Runs the given action with {@code leftPos}, {@code topPos}, {@code imageWidth} and {@code imageHeight} set to the main panel
   * ({@link #cornerX}, {@link #cornerY}, {@link #realWidth}, {@link #realHeight}) instead of the bounds of all modules.
   * Vanilla positions slots, labels and the background relative to those fields, while input handling uses the full bounds.
   */
  protected void runWithMainBounds(Runnable action) {
    int oldX = this.leftPos;
    int oldY = this.topPos;
    int oldW = this.imageWidth;
    int oldH = this.imageHeight;

    this.leftPos = this.cornerX;
    this.topPos = this.cornerY;
    this.imageWidth = this.realWidth;
    this.imageHeight = this.realHeight;
    try {
      action.run();
    } finally {
      this.leftPos = oldX;
      this.topPos = oldY;
      this.imageWidth = oldW;
      this.imageHeight = oldH;
    }
  }

  /**
   * 26.3: vanilla draws the background through this method on its own stratum before {@link #extractRenderState(GuiGraphicsExtractor, int, int, float)}.
   * This applies the main panel bounds and then calls {@link #renderBg(GuiGraphicsExtractor, float, int, int)}.
   * Final so subclasses override that instead: anything drawn here before the super call would end up under the dim overlay.
   */
  @Override
  public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
    // dims the world behind the screen
    super.extractBackground(graphics, mouseX, mouseY, partialTicks);
    this.runWithMainBounds(() -> this.renderBg(graphics, partialTicks, mouseX, mouseY));
  }

  /**
   * Draws the background of this screen and all modules.
   * Called with the main panel bounds applied, so {@code leftPos} and {@code topPos} match {@link #cornerX} and {@link #cornerY}.
   */
  protected void renderBg(GuiGraphicsExtractor graphics, float partialTicks, int mouseX, int mouseY) {
    for (ModuleScreen<?,?> module : this.modules) {
      module.handleDrawGuiContainerBackgroundLayer(graphics, partialTicks, mouseX, mouseY);
    }
  }

  @Override
  protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    this.drawContainerName(graphics);
    this.drawPlayerInventoryName(graphics);

    Matrix3x2fStack poses = graphics.pose();
    for (ModuleScreen<?,?> module : this.modules) {
      // set correct state for the module
      poses.pushMatrix();
      poses.translate(module.getLeftPos() - this.leftPos, module.getTopPos() - this.topPos);
      module.handleDrawGuiContainerForegroundLayer(graphics, mouseX, mouseY);
      poses.popMatrix();
    }
  }

  @Override
  protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    super.extractTooltip(graphics, mouseX, mouseY);

    for (ModuleScreen<?,?> module : this.modules) {
      module.handleRenderHoveredTooltip(graphics, mouseX, mouseY);
    }
  }

  protected void drawBackground(GuiGraphicsExtractor graphics, Identifier background) {
    graphics.blit(RenderPipelines.GUI_TEXTURED, background, this.cornerX, this.cornerY, 0, 0, this.realWidth, this.realHeight, 256, 256);
  }

  protected void drawContainerName(GuiGraphicsExtractor graphics) {
    graphics.text(this.font, this.getTitle().getVisualOrderText(), 8, 6, 0xFF404040, false);
  }

  protected void drawPlayerInventoryName(GuiGraphicsExtractor graphics) {
    assert Minecraft.getInstance().player != null;
    Component localizedName = Minecraft.getInstance().player.getInventory().getDisplayName();
    graphics.text(this.font, localizedName.getVisualOrderText(), 8, this.imageHeight - 96 + 2, 0xFF404040, false);
  }

  @Override
  public void resize(int width, int height) {
    super.resize(width, height);

    for (ModuleScreen<?,?> module : this.modules) {
      module.resize(width, height);
      this.updateSubmodule(module);
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
    // 26.3: the background (dim overlay + renderBg) is drawn by extractBackground before this, and the base method
    // already calls extractTooltip, so no manual background or tooltip call here (either would draw twice).
    // We set the main panel bounds so slots and labels are positioned relative to the main panel.
    this.runWithMainBounds(() -> super.extractRenderState(graphics, mouseX, mouseY, partialTicks));
  }

  // needed to get the correct slot on clicking
  @Override
  protected boolean isHovering(int left, int top, int width, int height, double pointX, double pointY) {
    pointX -= this.cornerX;
    pointY -= this.cornerY;
    return pointX >= left - 1 && pointX < left + width + 1 && pointY >= top - 1 && pointY < top + height + 1;
  }

  protected void updateSubmodule(ModuleScreen<?,?> module) {
    module.updatePosition(this.cornerX, this.cornerY, this.realWidth, this.realHeight);

    int moduleLeft = module.getLeftPos();
    int moduleTop = module.getTopPos();
    if (moduleLeft < this.leftPos) {
      this.imageWidth += this.leftPos - moduleLeft;
      this.leftPos = moduleLeft;
    }

    if (moduleTop < this.topPos) {
      this.imageHeight += this.topPos - moduleTop;
      this.topPos = moduleTop;
    }

    if (module.guiRight() > this.leftPos + this.imageWidth) {
      this.imageWidth = module.guiRight() - this.leftPos;
    }

    if (module.guiBottom() > this.topPos + this.imageHeight) {
      this.imageHeight = module.guiBottom() - this.topPos;
    }
  }

  @Override
  public void extractSlot(GuiGraphicsExtractor graphics, Slot slotIn, int mouseX, int mouseY) {
    ModuleScreen<?,?> module = this.getModuleForSlot(slotIn.index);

    if (module != null) {
      Slot slot = slotIn;
      // unwrap for the call to the module
      if (slotIn instanceof WrapperSlot) {
        slot = ((WrapperSlot) slotIn).parent;
      }

      if (!module.shouldDrawSlot(slot)) {
        return;
      }
    }

    // Sync the wrapper to its parent's CURRENT position. Module screens reposition their real slots
    // in updateSlots(), but the menu renders these wrapper copies, whose x/y were captured at
    // construction. Without this, side-inventory items render at stale positions and only appear
    // after a reopen. Slot.x/y are made mutable by the access transformer (updateSlots writes them), so this is safe.
    if (slotIn instanceof WrapperSlot wrapper) {
      slotIn.x = wrapper.parent.x;
      slotIn.y = wrapper.parent.y;
    }

    super.extractSlot(graphics, slotIn, mouseX, mouseY);
  }

  @Override
  public boolean isHovering(Slot slotIn, double mouseX, double mouseY) {
    ModuleScreen<?,?> module = this.getModuleForSlot(slotIn.index);

    // mouse inside the module of the slot?
    if (module != null) {
      Slot slot = slotIn;
      // unwrap for the call to the module
      if (slotIn instanceof WrapperSlot) {
        slot = ((WrapperSlot) slotIn).parent;
      }

      if (!module.shouldDrawSlot(slot)) {
        return false;
      }
    }

    return super.isHovering(slotIn, mouseX, mouseY);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    ModuleScreen<?,?> module = this.getModuleForPoint(event.x(), event.y());

    if (module != null) {
      // the button follows the 26.3 numbering, see InputConstants.MOUSE_BUTTON_LEFT
      if (module.handleMouseClicked(event.x(), event.y(), event.button())) {
        return false;
      }
    }

    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
    ModuleScreen<?,?> module = this.getModuleForPoint(event.x(), event.y());

    if (module != null) {
      if (module.handleMouseClickMove(event.x(), event.y(), event.button(), dragX)) {
        return false;
      }
    }

    return super.mouseDragged(event, dragX, dragY);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    ModuleScreen<?,?> module = this.getModuleForPoint(mouseX, mouseY);

    if (module != null) {
      if (module.handleMouseScrolled(mouseX, mouseY, scrollY)) {
        return false;
      }
    }

    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    // every module gets the release, even ones the cursor has left; a module tracking a drag needs the button up to stop tracking
    // the default implementation still bounds checks before running the module's own handler, so only modules that opt in see releases outside their area
    boolean handled = false;
    for (ModuleScreen<?,?> module : this.modules) {
      if (module.handleMouseReleased(event.x(), event.y(), event.button())) {
        handled = true;
      }
    }

    if (handled) {
      return false;
    }

    return super.mouseReleased(event);
  }

  @Nullable
  protected ModuleScreen<?,?> getModuleForPoint(double x, double y) {
    for (ModuleScreen<?,?> module : this.modules) {
      if (this.isHovering(module.getLeftPos(), module.getTopPos(), module.getImageWidth(), module.getImageHeight(), x + this.cornerX, y + this.cornerY)) {
        return module;
      }
    }

    return null;
  }

  @Nullable
  protected ModuleScreen<?,?> getModuleForSlot(int slotNumber) {
    return this.getModuleForContainer(this.getMenu().getSlotContainer(slotNumber));
  }

  @Nullable
  protected ModuleScreen<?,?> getModuleForContainer(AbstractContainerMenu container) {
    for (ModuleScreen<?,?> module : this.modules) {
      if (module.getMenu() == container) {
        return module;
      }
    }

    return null;
  }

  @Override
  public CONTAINER getMenu() {
    return this.menu;
  }
}
