package slimeknights.mantle.client.screen.book.element;

import com.mojang.math.Transformation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.joml.AxisAngle4f;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.client.book.structure.StructureInfo;
import slimeknights.mantle.client.book.structure.StructurePreviewRenderState;
import slimeknights.mantle.client.book.structure.level.TemplateLevel;
import slimeknights.mantle.client.screen.book.BookScreen;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Element drawing a rotatable 3D preview of a structure, optionally animating it block by block.
 * Rendering happens in {@link slimeknights.mantle.client.book.structure.StructurePreviewRenderer}.
 */
public class StructureElement extends SizedBookElement {

  public boolean canTick = false;

  public float scale = 50f;
  public float transX = 0;
  public float transY = 0;
  public Transformation additionalTransform;
  public final StructureInfo renderInfo;
  public final TemplateLevel structureWorld;

  public long lastStep = -1;
  /** @deprecated errors are now logged by {@link slimeknights.mantle.client.book.structure.StructurePreviewRenderer} */
  @Deprecated
  public long lastPrintedErrorTimeMs = -1;

  public StructureElement(int x, int y, int width, int height, StructureTemplate template, List<StructureTemplate.StructureBlockInfo> structure) {
    super(x, y, width, height);

    int[] size = {template.getSize().getX(), template.getSize().getY(), template.getSize().getZ()};

    this.scale = 100f / (float) IntStream.of(size).max().getAsInt();

    float sx = (float) width / (float) BookScreen.PAGE_WIDTH;
    float sy = (float) height / (float) BookScreen.PAGE_HEIGHT;

    this.scale *= Math.min(sx, sy);

    this.renderInfo = new StructureInfo(structure);

    this.structureWorld = new TemplateLevel(structure, renderInfo);

    this.transX = x + width / 2F;
    this.transY = y + height / 2F;

    this.additionalTransform = new Transformation(null, new Quaternionf().rotateYXZ(0, (float)(25 * Math.PI / 180f), 0), null, new Quaternionf().rotateYXZ((float)(-45 * Math.PI / 180f), 0, 0));
  }

  @Override
  public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer) {
    long currentTime = System.currentTimeMillis();

    if (this.lastStep < 0)
      this.lastStep = currentTime;
    else if (this.canTick && currentTime - this.lastStep > 200) {
      this.renderInfo.step();
      this.lastStep = currentTime;
    }

    if (!this.canTick) {
      this.renderInfo.reset();
    }

    // 3D content in the GUI renders into a texture, which is then drawn on the page
    // the texture covers the whole page so the structure can still extend past the element when rotated
    int x0 = Math.min(0, this.x);
    int y0 = Math.min(0, this.y);
    int x1 = Math.max(BookScreen.PAGE_WIDTH, this.x + this.width);
    int y1 = Math.max(BookScreen.PAGE_HEIGHT, this.y + this.height);
    graphics.submitPictureInPictureRenderState(new StructurePreviewRenderState(
      this.structureWorld, this.renderInfo.structureLength, this.renderInfo.structureHeight, this.renderInfo.structureWidth, this.renderInfo.getLimiter(),
      this.additionalTransform, this.transX - (x0 + x1) / 2f, this.transY - (y0 + y1) / 2f,
      x0, y0, x1, y1, this.scale, new Matrix3x2f(graphics.pose()), graphics.peekScissorStack()));
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
    super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public void mouseDragged(double clickX, double clickY, double mouseX, double mouseY, double lastX, double lastY, int button) {
    double dx = mouseX - lastX;
    double dy = mouseY - lastY;
    this.additionalTransform = forRotation(dx * 80D / 104, dy * 0.8).compose(this.additionalTransform);
  }

  @Override
  public void mouseReleased(double mouseX, double mouseY, int clickedMouseButton) {
    super.mouseReleased(mouseX, mouseY, clickedMouseButton);
  }

  private Transformation forRotation(double rX, double rY) {
    Vector3f axis = new Vector3f((float) rY, (float) rX, 0);
    float dot = axis.dot(axis);
    if (dot < Float.MIN_NORMAL) {
      return Transformation.IDENTITY;
    }

    float angle = (float) (Math.sqrt(axis.dot(axis)) * Math.PI / 180f);
    axis.normalize();
    return new Transformation(null, new Quaternionf(new AxisAngle4f(angle, axis)), null, null);
  }
}
