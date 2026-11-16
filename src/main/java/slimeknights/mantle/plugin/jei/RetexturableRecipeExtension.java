package slimeknights.mantle.plugin.jei;

import com.google.common.collect.Streams;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.crafting.ShapedRetexturedRecipe;
import slimeknights.mantle.recipe.helper.IngredientHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * JEI crafting extension to properly show, animate, and focus {@link ShapedRetexturedRecipe} instances.
 * <p>
 * JEI 19.x changed {@code ICraftingCategoryExtension} to be generic and stateless: a single extension instance is
 * registered per recipe class and receives the {@link RecipeHolder} on each call (the holder carries the id, which
 * recipes no longer expose directly). All per-recipe data is therefore computed inside the hook methods.
 */
public class RetexturableRecipeExtension implements ICraftingCategoryExtension<ShapedRetexturedRecipe> {

  /** List of all textured variants, fallback for JEI display */
  private static List<ItemStack> getDisplayOutputs(ShapedRetexturedRecipe recipe) {
    // set the output to display all variants from the texture ingredient
    Ingredient texture = recipe.getTexture();
    // fetch all stacks from the ingredient, note any variants that are not blocks will get a blank look
    List<ItemStack> displayOutputs = IngredientHelper.getStacks(texture).stream()
                                                     .map(stack -> recipe.getResultItem(stack.getItem()))
                                                     .toList();
    // empty display means the tag found nothing, so just use the original output
    return displayOutputs.isEmpty() ? List.of(recipe.getResultItem()) : displayOutputs;
  }

  /** Ingredient indexes of all texture slots */
  private static int[] getTextureSlots(ShapedRetexturedRecipe recipe) {
    Ingredient texture = recipe.getTexture();
    // find out which inputs match the texture, we will need to use those for the focus link
    List<Optional<Ingredient>> inputs = recipe.getIngredients();
    return IntStream.range(0, inputs.size()).filter(i -> inputs.get(i).filter(input -> ingredientsMatch(texture, input)).isPresent()).toArray();
  }

  /** Checks if two ingredients match based on their display items */
  private static boolean ingredientsMatch(Ingredient left, Ingredient right) {
    if (left.equals(right)) {
      return true;
    }
    List<ItemStack> leftStacks = IngredientHelper.getStacks(left);
    List<ItemStack> rightStacks = IngredientHelper.getStacks(right);
    if (leftStacks.size() != rightStacks.size()) {
      return false;
    }
    for (int i = 0; i < leftStacks.size(); i++) {
      if (!ItemStack.isSameItemSameComponents(leftStacks.get(i), rightStacks.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public List<SlotDisplay> getIngredients(RecipeHolder<ShapedRetexturedRecipe> holder) {
    return holder.value().getIngredients().stream().map(Ingredient::optionalIngredientToDisplay).toList();
  }

  @Override
  public int getWidth(RecipeHolder<ShapedRetexturedRecipe> holder) {
    return holder.value().getWidth();
  }

  @Override
  public int getHeight(RecipeHolder<ShapedRetexturedRecipe> holder) {
    return holder.value().getHeight();
  }

  @Override
  public void setRecipe(RecipeHolder<ShapedRetexturedRecipe> holder, IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
    ShapedRetexturedRecipe recipe = holder.value();
    List<ItemStack> displayOutputs = getDisplayOutputs(recipe);
    int[] textureSlots = getTextureSlots(recipe);

    // we need the blank version for the sake of recipe lookup due to the subtype interpreter making it not the same
    builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).add(recipe.getResultItem());

    // add the itemstacks to the grid, empty slots have no ingredient
    List<List<ItemStack>> inputStacks = recipe.getIngredients().stream().map(ingredient -> ingredient.map(IngredientHelper::getStacks).orElse(List.of())).toList();
    int width = recipe.getWidth();
    int height = recipe.getHeight();
    List<IRecipeSlotBuilder> inputs = craftingGridHelper.createAndSetInputs(builder, inputStacks, width, height);
    IRecipeSlotBuilder output = craftingGridHelper.createAndSetOutputs(builder, displayOutputs);
    if (inputs.size() != 9) {
      Mantle.logger.error("Failed to create focus link for {} as the layout {} is not 3x3", holder.id(), builder.getClass().getName());
    } else {
      // link the output to all inputs that match the texture
      builder.createFocusLink(Streams.concat(Stream.of(output), Arrays.stream(textureSlots).mapToObj(i -> inputs.get(craftingGridHelper.getCraftingIndex(i, width, height)))).toArray(IRecipeSlotBuilder[]::new));
    }
  }
}
