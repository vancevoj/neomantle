package slimeknights.mantle.client.screen.book.element;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;
import slimeknights.mantle.client.book.data.element.ImageData;
import slimeknights.mantle.client.screen.book.BookScreen;

import static java.util.Objects.requireNonNullElse;

public class ImageElement extends SizedBookElement {

  public ImageData image;
  public int colorMultiplier;

  private ItemElement itemElement;

  public ImageElement(ImageData image) {
    this(image, 0xFFFFFF);
  }

  public ImageElement(ImageData image, int colorMultiplier) {
    this(image.x, image.y, image.width, image.height, image, colorMultiplier);
  }

  public ImageElement(int x, int y, int width, int height, ImageData image) {
    this(x, y, width, height, image, image.colorMultiplier);
  }

  public ImageElement(int x, int y, int width, int height, ImageData image, int colorMultiplier) {
    super(x, y, width, height);

    this.image = image;

    if (image.x != -1) {
      x = image.x;
    }
    if (image.y != -1) {
      y = image.y;
    }
    if (image.width != -1) {
      width = image.width;
    }
    if (image.height != -1) {
      height = image.height;
    }
    if (image.colorMultiplier != 0xFFFFFF) {
      colorMultiplier = image.colorMultiplier;
    }

    this.x = x == -1 ? 0 : x;
    this.y = y == -1 ? 0 : y;
    this.width = width;
    this.height = height;
    this.colorMultiplier = colorMultiplier;

    if(image.item != null) {
      this.itemElement = new ItemElement(0, 0, 1F, image.item.getItems());
    }
  }

  @Override
  public void setParent(BookScreen parent) {
    super.setParent(parent);
    if (itemElement != null) {
      itemElement.setParent(parent);
    }
  }

  @Override
  public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer) {
    drawTinted(graphics, mouseX, mouseY, partialTicks, fontRenderer, 0xFFFFFFFF);
  }

  /**
   * Draws this element with an additional tint on top of {@link #colorMultiplier}.
   * Replaces setting the shader color before drawing, which is no longer possible.
   * @param tint  ARGB tint, only applies to textures as item icons cannot be tinted
   */
  public void drawTinted(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer, int tint) {
    if (this.image.item == null) {
      // color multiplier is RGB, tint carries the alpha
      int color = ARGB.multiply(ARGB.opaque(this.colorMultiplier), tint);
      Identifier texture = requireNonNullElse(this.image.location, MissingTextureAtlasSprite.getLocation());
      graphics.blit(RenderPipelines.GUI_TEXTURED, texture, this.x, this.y, this.image.u, this.image.v, this.width, this.height, this.image.uw, this.image.vh, this.image.texWidth, this.image.texHeight, color);
    }
    else {
      Matrix3x2fStack matrices = graphics.pose();
      matrices.pushMatrix();
      matrices.translate(this.x, this.y);
      matrices.scale(this.width / 16F, this.height / 16F);

      this.itemElement.draw(graphics, mouseX, mouseY, partialTicks, fontRenderer);

      matrices.popMatrix();
    }
  }
}
