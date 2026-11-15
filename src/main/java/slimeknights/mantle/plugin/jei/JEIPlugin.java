package slimeknights.mantle.plugin.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.screen.MultiModuleScreen;
import slimeknights.mantle.inventory.MultiModuleContainerMenu;
import slimeknights.mantle.plugin.jei.entity.EntityIngredientHelper;
import slimeknights.mantle.plugin.jei.entity.EntityIngredientRenderer;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.recipe.crafting.ShapedRetexturedRecipe;
import slimeknights.mantle.recipe.helper.RecipeHelper;
import slimeknights.mantle.recipe.ingredient.EntityIngredient.EntityInput;

import java.util.Collections;
import java.util.List;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
  @Override
  public Identifier getPluginUid() {
    return Mantle.getResource("jei");
  }

  @Override
  public void registerIngredients(IModIngredientRegistration registration) {
    registration.register(MantleJEIConstants.ENTITY_TYPE, Collections.emptyList(), new EntityIngredientHelper(), new EntityIngredientRenderer(16), EntityInput.CODEC);
  }

  /** Expands multirecipes for the given vanilla type into the given JEI category. */
  private static <C extends RecipeInput, V extends Recipe<C>> void addMultiRecipes(IRecipeRegistration registration, RegistryAccess access, RecipeMap recipes, RecipeType<V> vanillaType, IRecipeHolderType<V> jeiType, Class<V> recipeClass) {
    List<RecipeHolder<V>> expanded = IMultiRecipe.getMultiRecipes(access, recipes, vanillaType, recipeClass).toList();
    if (!expanded.isEmpty()) {
      registration.addRecipes(jeiType, expanded);
    }
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    Level level = Minecraft.getInstance().level;
    assert level != null;
    RegistryAccess access = level.registryAccess();
    // since 1.21.2 the client has no recipe manager, these types are requested for sync by RecipeHelper#sendRecipesToClient
    RecipeMap recipes = RecipeHelper.recipes(level);
    addMultiRecipes(registration, access, recipes, RecipeType.CRAFTING, RecipeTypes.CRAFTING, CraftingRecipe.class);
    addMultiRecipes(registration, access, recipes, RecipeType.SMELTING, RecipeTypes.SMELTING, SmeltingRecipe.class);
    addMultiRecipes(registration, access, recipes, RecipeType.SMOKING, RecipeTypes.SMOKING, SmokingRecipe.class);
    addMultiRecipes(registration, access, recipes, RecipeType.BLASTING, RecipeTypes.BLASTING, BlastingRecipe.class);
    addMultiRecipes(registration, access, recipes, RecipeType.CAMPFIRE_COOKING, RecipeTypes.CAMPFIRE_COOKING, CampfireCookingRecipe.class);
    addMultiRecipes(registration, access, recipes, RecipeType.STONECUTTING, RecipeTypes.STONECUTTING, StonecutterRecipe.class);
    addMultiRecipes(registration, access, recipes, RecipeType.SMITHING, RecipeTypes.SMITHING, SmithingRecipe.class);
  }

  @Override
  public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registry) {
    registry.getCraftingCategory().addExtension(ShapedRetexturedRecipe.class, new RetexturableRecipeExtension());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public void registerGuiHandlers(IGuiHandlerRegistration registration) {
    registration.addGuiContainerHandler(MultiModuleScreen.class, new MultiModuleContainerHandler());
  }

  private static class MultiModuleContainerHandler<C extends MultiModuleContainerMenu<?>> implements IGuiContainerHandler<MultiModuleScreen<C>> {
    @Override
    public List<Rect2i> getGuiExtraAreas(MultiModuleScreen<C> guiContainer) {
      return guiContainer.getModuleAreas();
    }
  }
}
