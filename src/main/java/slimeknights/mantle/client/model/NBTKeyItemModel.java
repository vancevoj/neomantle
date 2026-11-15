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
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Matrix4fc;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.ModelHelper;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Item model selecting a texture variant from {@link NBTKeyModel} using a string in the stack's custom data.
 * Used in client item JSON as {@code {"type": "mantle:nbt_key", "model": "mantle:item/example"}}, where the model uses the {@code mantle:nbt_key} loader.
 */
public class NBTKeyItemModel implements ItemModel {
  /** ID of this item model type */
  public static final Identifier ID = Mantle.getResource("nbt_key");

  /** Key to check in item NBT */
  private final String nbtKey;
  /** Model used when the key is missing or unknown */
  private final ItemModel defaultModel;
  /** Map of key value to model */
  private final Map<String,ItemModel> variants;
  /** Map of key value to texture */
  private final Map<String,Material.Baked> textures;

  public NBTKeyItemModel(String nbtKey, ItemModel defaultModel, Map<String,ItemModel> variants, Map<String,Material.Baked> textures) {
    this.nbtKey = nbtKey;
    this.defaultModel = defaultModel;
    this.variants = variants;
    this.textures = textures;
  }

  @Override
  public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
    ItemModel model = defaultModel;
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data != null && data.contains(nbtKey)) {
      model = variants.getOrDefault(data.copyTag().getStringOr(nbtKey, ""), defaultModel);
    }
    model.update(output, stack, resolver, displayContext, level, owner, seed);
  }

  /** Gets the given texture from the model, used for rendering the variant in other contexts such as JEI */
  @SuppressWarnings("unused")  // API usage
  public Material.Baked getTexture(String name) {
    Material.Baked texture = textures.get(name);
    return texture != null ? texture : textures.get("default");
  }

  /**
   * Unbaked model, parsed from client item JSON
   * @param model           Model location, should use the {@code mantle:nbt_key} loader
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
      ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, textureSlots);
      Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
      NBTKeyModel nbtModel = NBTKeyModel.find(resolved);
      if (nbtModel == null) {
        Mantle.logger.warn("Model {} used with item model type {} does not use the NBT key loader, variants will not work", model, ID);
        return new CuboidItemModelWrapper(List.of(), resolved.bakeTopGeometry(textureSlots, baker, BlockModelRotation.IDENTITY), properties, modelTransform);
      }

      // bake each variant
      ModelState state = ModelHelper.applyRootTransform(BlockModelRotation.IDENTITY, resolved.getTopAdditionalProperties());
      Map<String,ItemModel> variants = new HashMap<>();
      Map<String,Material.Baked> textures = new HashMap<>();
      ItemModel defaultModel = null;
      for (Entry<String,Material> entry : nbtModel.getTextures(resolved).entrySet()) {
        String key = entry.getKey();
        Material.Baked texture = baker.materials().get(entry.getValue(), resolved);
        textures.put(key, texture);
        // particle matches the variant
        ModelRenderProperties variantProperties = new ModelRenderProperties(properties.usesBlockLight(), texture, properties.transforms());
        ItemModel variant = new CuboidItemModelWrapper(List.of(), NBTKeyModel.bakeTexture(baker, texture, state), variantProperties, modelTransform);
        if (key.equals("default")) {
          defaultModel = variant;
        } else {
          variants.put(key, variant);
        }
      }
      // getTextures always includes default, but be safe
      if (defaultModel == null) {
        defaultModel = context.missingItemModel(modelTransform);
      }
      return new NBTKeyItemModel(nbtModel.getNbtKey(), defaultModel, Map.copyOf(variants), Map.copyOf(textures));
    }
  }
}
