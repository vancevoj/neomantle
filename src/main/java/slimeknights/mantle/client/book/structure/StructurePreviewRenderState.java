package slimeknights.mantle.client.book.structure;

import com.mojang.math.Transformation;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2fc;
import slimeknights.mantle.client.book.structure.level.TemplateLevel;

import javax.annotation.Nullable;

/**
 * Picture in picture state for the book structure preview, rendered by {@link StructurePreviewRenderer}.
 * All values are immutable copies taken at extraction time, as the renderer runs later in the frame.
 * Equal states render the same image, which lets the renderer reuse the texture of the previous frame.
 * @param level            Level containing the structure blocks
 * @param structureLength  Size of the structure along X
 * @param structureHeight  Size of the structure along Y
 * @param structureWidth   Size of the structure along Z
 * @param limiter          Last block index shown, see {@link StructureInfo#getLimiter()}
 * @param rotation         Rotation of the structure, applied around its center
 * @param offsetX          X offset in GUI pixels from the center of the area to the center of the structure
 * @param offsetY          Y offset in GUI pixels from the center of the area to the center of the structure
 * @param x0               Left of the area, relative to {@code pose}
 * @param y0               Top of the area, relative to {@code pose}
 * @param x1               Right of the area, relative to {@code pose}
 * @param y1               Bottom of the area, relative to {@code pose}
 * @param scale            GUI pixels per block
 * @param pose             Pose of the GUI when the structure was drawn, typically the page transform
 * @param scissorArea      Active scissor area
 * @param bounds           Screen bounds of the area after the pose and scissor
 */
public record StructurePreviewRenderState(
  TemplateLevel level, int structureLength, int structureHeight, int structureWidth, int limiter, Transformation rotation, float offsetX, float offsetY,
  int x0, int y0, int x1, int y1, float scale, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
  public StructurePreviewRenderState(
    TemplateLevel level, int structureLength, int structureHeight, int structureWidth, int limiter, Transformation rotation, float offsetX, float offsetY,
    int x0, int y0, int x1, int y1, float scale, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea) {
    this(level, structureLength, structureHeight, structureWidth, limiter, rotation, offsetX, offsetY, x0, y0, x1, y1, scale, pose, scissorArea,
         PictureInPictureRenderState.getBounds(x0, y0, x1, y1, pose, scissorArea));
  }
}
