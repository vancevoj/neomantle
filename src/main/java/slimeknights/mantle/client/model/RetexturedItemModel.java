package slimeknights.mantle.client.model;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4fc;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.GeometryContextWrapper;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.util.RetexturedHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item model for {@link RetexturedModel}, swapping the retextured textures for the particle texture of the block stored on the stack.
 * Used in client item JSON as {@code {"type": "mantle:retextured", "model": "mantle:block/example"}}.
 */
public class RetexturedItemModel implements ItemModel {
  /** ID of this item model type */
  public static final Identifier ID = Mantle.getResource("retextured");

  /** Model used when the stack has no texture */
  private final ItemModel base;
  /** Model logic for baking */
  private final RetexturedModel model;
  /** Baker used to rebake quads */
  private final ModelBaker baker;
  /** Elements in the model, may come from a parent */
  private final List<CuboidModelElement> elements;
  /** Original materials of the model */
  private final GeometryContextWrapper materials;
  /** Model transforms for quads, including the root transform */
  private final ModelState state;
  /** List of texture names that are retextured, including texture references */
  private final Set<String> retextured;
  /** Render properties of the original model */
  private final ModelRenderProperties properties;
  /** Item transform to apply */
  private final Matrix4fc transformation;
  /** Cache of retextured item models, the model is rebaked on resource reload so no need to clear it */
  private final Map<Material.Baked,ItemModel> cache = new ConcurrentHashMap<>();

  public RetexturedItemModel(ItemModel base, ModelBaker baker, ResolvedModel resolved, RetexturedModel model, ModelRenderProperties properties, Matrix4fc transformation) {
    this.base = base;
    this.model = model;
    this.baker = ModelHelper.dynamicBaker(baker);
    this.elements = model.getElements(resolved);
    String debugName = resolved.debugName();
    this.materials = new GeometryContextWrapper(this.baker, resolved.getTopTextureSlots(), () -> debugName);
    this.state = ModelHelper.applyRootTransform(BlockModelRotation.IDENTITY, resolved.getTopAdditionalProperties());
    this.retextured = model.getAllRetextured(resolved);
    this.properties = properties;
    this.transformation = transformation;
  }

  /**
   * Gets the model with the given texture applied
   * @param texture  Texture material
   * @return  Retextured model
   */
  private ItemModel getRetexturedModel(Material.Baked texture) {
    QuadCollection quads = model.bakeWithElements(baker, elements, new RetexturedModel.RetexturedContext(materials, retextured, texture), state);
    ModelRenderProperties properties = this.properties;
    if (retextured.contains("particle")) {
      properties = new ModelRenderProperties(properties.usesBlockLight(), texture, properties.transforms());
    }
    return new CuboidItemModelWrapper(List.of(), quads, properties, transformation);
  }

  /**
   * Gets a cached retextured model, computing it if missing from the cache
   * @param block  Block determining the texture
   * @return  Retextured model
   */
  public ItemModel getCachedModel(Block block) {
    return cache.computeIfAbsent(ModelHelper.getParticleMaterial(block), this::getRetexturedModel);
  }

  @Override
  public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
    if (!stack.isEmpty()) {
      // get the block first, ensuring its valid; getTexture returns AIR when no texture is stored
      Block block = RetexturedHelper.getTexture(stack);
      if (block != Blocks.AIR) {
        getCachedModel(block).update(output, stack, resolver, displayContext, level, owner, seed);
        return;
      }
    }
    base.update(output, stack, resolver, displayContext, level, owner, seed);
  }

  /**
   * Unbaked model, parsed from client item JSON
   * @param model           Model location, should use the {@code mantle:retextured} loader
   * @param transformation  Extra transformation to apply to the model
   */
  public record Unbaked(Identifier model, Optional<Transformation> transformation) implements ItemModel.Unbaked {
    public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
      Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation)
    ).apply(inst, Unbaked::new));

    public Unbaked(Identifier model) {
      this(model, Optional.empty());
    }

    @Override
    public MapCodec<Unbaked> type() {
      return MAP_CODEC;
    }

    @Override
    public void resolveDependencies(ResolvableModel.Resolver resolver) {
      resolver.markDependency(model);
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
      ModelBaker baker = context.blockModelBaker();
      ResolvedModel resolved = baker.getModel(model);
      TextureSlots textureSlots = resolved.getTopTextureSlots();
      QuadCollection quads = resolved.bakeTopGeometry(textureSlots, baker, BlockModelRotation.IDENTITY);
      ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, textureSlots);
      Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
      ItemModel base = new CuboidItemModelWrapper(List.of(), quads, properties, modelTransform);
      RetexturedModel retexturedModel = RetexturedModel.find(resolved);
      if (retexturedModel == null) {
        Mantle.logger.warn("Model {} used with item model type {} does not use the retextured loader, it will not be retextured", model, ID);
        return base;
      }
      return new RetexturedItemModel(base, baker, resolved, retexturedModel, properties, modelTransform);
    }
  }
}
