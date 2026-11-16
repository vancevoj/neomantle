package slimeknights.mantle.recipe.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.mantle.recipe.helper.RecipeHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class ShapedFallbackRecipe extends ShapedRecipe {
  /** Map codec for the recipe, JSON matches vanilla shaped recipes with the added alternatives list */
  public static final MapCodec<ShapedFallbackRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
    ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
    ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
    Identifier.CODEC.listOf().optionalFieldOf("alternatives", List.of()).forGetter(r -> r.alternatives)
  ).apply(inst, ShapedFallbackRecipe::new));
  /** Stream codec for the recipe */
  public static final StreamCodec<RegistryFriendlyByteBuf,ShapedFallbackRecipe> STREAM_CODEC = StreamCodec.composite(
    Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
    CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
    ShapedRecipePattern.STREAM_CODEC, r -> r.pattern,
    ItemStackTemplate.STREAM_CODEC, r -> r.result,
    Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.alternatives,
    ShapedFallbackRecipe::new);

  /** Copy of the result, as the vanilla field is private */
  private final ItemStackTemplate result;
  /** Recipes to skip if they match */
  private final List<Identifier> alternatives;
  /** Recipe map used to compute {@link #alternativeCache}, recomputed if the recipes change */
  @Nullable
  private RecipeMap alternativeSource;
  private List<CraftingRecipe> alternativeCache;

  /**
   * Main constructor, creates a recipe from all parameters
   * @param commonInfo    Shared recipe info, contains the notification toggle
   * @param bookInfo      Recipe book info, contains the group and category
   * @param pattern       Recipe pattern (width, height, ingredients)
   * @param result        Recipe output
   * @param alternatives  List of recipe names to fail this match if they match
   */
  public ShapedFallbackRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result, List<Identifier> alternatives) {
    super(commonInfo, bookInfo, pattern, result);
    this.result = result;
    this.alternatives = List.copyOf(alternatives);
  }

  /**
   * Creates a recipe from all parameters, adapter for the pre 26.1 parameter list
   * @param group             Recipe group
   * @param category          Recipe book category
   * @param pattern           Recipe pattern (width, height, ingredients)
   * @param result            Recipe output
   * @param showNotification  If true, shows the toast notification on first craft
   * @param alternatives      List of recipe names to fail this match if they match
   */
  public ShapedFallbackRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification, List<Identifier> alternatives) {
    this(new Recipe.CommonInfo(showNotification), new CraftingRecipe.CraftingBookInfo(category, group), pattern, ItemStackTemplate.fromNonEmptyStack(result), alternatives);
  }

  /**
   * Creates a recipe copying the given shaped recipe
   * @param shaped        Shaped recipe to copy
   * @param alternatives  List of recipe names to fail this match if they match
   */
  public ShapedFallbackRecipe(ShapedRecipe shaped, List<Identifier> alternatives) {
    this(new Recipe.CommonInfo(shaped.showNotification()), new CraftingRecipe.CraftingBookInfo(shaped.category(), shaped.group()), shaped.pattern, ShapedRetexturedRecipe.resultTemplate(shaped), alternatives);
  }

  /** Gets the result template */
  ItemStackTemplate getResult() {
    return result;
  }

  /** Gets the list of alternative recipe IDs */
  public List<Identifier> getAlternatives() {
    return alternatives;
  }

  @Override
  public boolean matches(CraftingInput inv, Level world) {
    // if this recipe does not match, fail it
    if (!super.matches(inv, world)) {
      return false;
    }

    // fetch all alternatives, fail if any match
    // cache to save effort down the line, recompute if the recipe map changed (client sync or reload)
    RecipeMap recipes = RecipeHelper.recipes(world);
    if (alternativeCache == null || alternativeSource != recipes) {
      List<CraftingRecipe> cache = new ArrayList<>();
      for (Identifier alternative : alternatives) {
        RecipeHolder<?> holder = recipes.byKey(RecipeHelper.key(alternative));
        if (holder != null) {
          // only allow exact shaped or shapeless match, prevent infinite recursion due to complex recipes
          Recipe<?> recipe = holder.value();
          Class<?> clazz = recipe.getClass();
          if (clazz == ShapedRecipe.class || clazz == ShapelessRecipe.class) {
            cache.add((CraftingRecipe) recipe);
          }
        }
      }
      alternativeCache = List.copyOf(cache);
      alternativeSource = recipes;
    }
    // fail if any alternative matches
    return this.alternativeCache.stream().noneMatch(recipe -> recipe.matches(inv, world));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})  // vanilla types the serializer as the parent class
  @Override
  public RecipeSerializer<ShapedRecipe> getSerializer() {
    return (RecipeSerializer) MantleRecipes.CRAFTING_SHAPED_FALLBACK.get();
  }

  /** Serializer logic, register {@link #toSerializer()} */
  public static class Serializer implements LoggingRecipeSerializer<ShapedFallbackRecipe> {
    @Override
    public MapCodec<ShapedFallbackRecipe> codec() {
      return MAP_CODEC;
    }

    @Override
    public ShapedFallbackRecipe fromNetworkSafe(RegistryFriendlyByteBuf buffer) {
      return STREAM_CODEC.decode(buffer);
    }

    @Override
    public void toNetworkSafe(RegistryFriendlyByteBuf buffer, ShapedFallbackRecipe recipe) {
      STREAM_CODEC.encode(buffer, recipe);
    }
  }
}
