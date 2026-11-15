package slimeknights.mantle.recipe.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.mantle.util.RetexturedHelper;

/** Recipe which sets the texture for a {@link slimeknights.mantle.block.RetexturedBlock} based on an ingredient input. */
// TODO 1.21: rework to be more like the ShapedMaterialsRecipe from Tinkers for more efficient network syncing
@SuppressWarnings("WeakerAccess")
public class ShapedRetexturedRecipe extends ShapedRecipe {
  /** Map codec for the recipe, JSON matches vanilla shaped recipes with the added texture fields */
  public static final MapCodec<ShapedRetexturedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
    ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
    ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
    Ingredient.CODEC.fieldOf("texture").forGetter(r -> r.texture),
    Codec.BOOL.optionalFieldOf("match_all", false).forGetter(r -> r.matchAll)
  ).apply(inst, ShapedRetexturedRecipe::new));
  /** Stream codec for the recipe */
  public static final StreamCodec<RegistryFriendlyByteBuf,ShapedRetexturedRecipe> STREAM_CODEC = StreamCodec.composite(
    Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
    CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
    ShapedRecipePattern.STREAM_CODEC, r -> r.pattern,
    ItemStackTemplate.STREAM_CODEC, r -> r.result,
    Ingredient.CONTENTS_STREAM_CODEC, r -> r.texture,
    ByteBufCodecs.BOOL, r -> r.matchAll,
    ShapedRetexturedRecipe::new);

  /** Ingredient used to determine the texture on the output */
  @Getter
  private final Ingredient texture;
  private final boolean matchAll;
  /** Copy of the result, as the vanilla field is private */
  private final ItemStackTemplate result;

  /** Creates a new recipe using the passed parameters */
  protected ShapedRetexturedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result, Ingredient texture, boolean matchAll) {
    super(commonInfo, bookInfo, pattern, result);
    this.result = result;
    this.texture = texture;
    this.matchAll = matchAll;
  }

  /** Creates a new recipe using the passed parameters, adapter for the pre 26.1 parameter list */
  protected ShapedRetexturedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification, Ingredient texture, boolean matchAll) {
    this(new Recipe.CommonInfo(showNotification), new CraftingRecipe.CraftingBookInfo(category, group), pattern, ItemStackTemplate.fromNonEmptyStack(result), texture, matchAll);
  }

  /**
   * Creates a new recipe copying the given shaped recipe
   * @param shaped    Shaped recipe to copy
   * @param texture   Ingredient to use for the texture
   * @param matchAll  If true, all texture inputs must match
   */
  protected ShapedRetexturedRecipe(ShapedRecipe shaped, Ingredient texture, boolean matchAll) {
    this(new Recipe.CommonInfo(shaped.showNotification()), new CraftingRecipe.CraftingBookInfo(shaped.category(), shaped.group()), shaped.pattern, resultTemplate(shaped), texture, matchAll);
  }

  /**
   * Gets the result template of a shaped recipe, as the vanilla field is private.
   * @param recipe  Shaped recipe
   * @return  Result template
   */
  static ItemStackTemplate resultTemplate(ShapedRecipe recipe) {
    if (recipe instanceof ShapedRetexturedRecipe retextured) {
      return retextured.result;
    }
    if (recipe instanceof ShapedFallbackRecipe fallback) {
      return fallback.getResult();
    }
    // vanilla shaped recipes display their exact result template
    for (RecipeDisplay display : recipe.display()) {
      if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate stack)) {
        return stack;
      }
    }
    // shaped recipes ignore the input to assemble
    return ItemStackTemplate.fromNonEmptyStack(recipe.assemble(CraftingInput.EMPTY));
  }

  /** Gets the untextured result of this recipe */
  public ItemStack getResultItem() {
    return result.create();
  }

  /** @deprecated use {@link #getResultItem()}, registries are no longer needed */
  @Deprecated
  public ItemStack getResultItem(HolderLookup.Provider registries) {
    return getResultItem();
  }

  /**
   * Gets the output using the given texture
   * @param texture  Texture to use
   * @return  Output with texture. Will be blank if the input is not a block
   */
  public ItemStack getResultItem(Item texture) {
    return RetexturedHelper.setTexture(getResultItem(), Block.byItem(texture));
  }

  /** @deprecated use {@link #getResultItem(Item)}, registries are no longer needed */
  @Deprecated
  public ItemStack getResultItem(Item texture, HolderLookup.Provider registries) {
    return getResultItem(texture);
  }

  @Override
  public ItemStack assemble(CraftingInput input) {
    ItemStack result = super.assemble(input);
    Block currentTexture = null;
    for (int i = 0; i < input.size(); i++) {
      ItemStack stack = input.getItem(i);
      if (!stack.isEmpty() && texture.test(stack)) {
        // fetch texture from the block if it has one
        Block block = RetexturedHelper.getTexture(stack);
        // assuming it does not, use the block itself as the texture (provided it is not the result that is)
        if (block == Blocks.AIR && stack.getItem() != result.getItem()) {
          block = Block.byItem(stack.getItem());
        }
        // if no texture, skip
        if (block == Blocks.AIR) {
          continue;
        }

        // if we have not found a texture yet, store the found block
        if (currentTexture == null) {
          currentTexture = block;
          // match all means we must check the rest. If not match all, we can be done
          if (!matchAll) {
            break;
          }

          // if we found a texture before, must match or we do no texture
        } else if (currentTexture != block) {
          currentTexture = null;
          break;
        }
      }
    }

    // set the texture if found. No texture will use the fallback
    if (currentTexture != null) {
      return RetexturedHelper.setTexture(result, currentTexture);
    }
    return result;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})  // vanilla types the serializer as the parent class
  @Override
  public RecipeSerializer<ShapedRecipe> getSerializer() {
    return (RecipeSerializer) MantleRecipes.CRAFTING_SHAPED_RETEXTURED.get();
  }

  /** Serializer logic, register {@link #toSerializer()} */
  public static class Serializer implements LoggingRecipeSerializer<ShapedRetexturedRecipe> {
    @Override
    public MapCodec<ShapedRetexturedRecipe> codec() {
      return MAP_CODEC;
    }

    @Override
    public ShapedRetexturedRecipe fromNetworkSafe(RegistryFriendlyByteBuf buffer) {
      return STREAM_CODEC.decode(buffer);
    }

    @Override
    public void toNetworkSafe(RegistryFriendlyByteBuf buffer, ShapedRetexturedRecipe recipe) {
      STREAM_CODEC.encode(buffer, recipe);
    }
  }
}
