package com.zahrproject.votingmod.recipe;

import com.zahrproject.votingmod.VotingMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, VotingMod.MOD_ID);

    public static final RegistryObject<SimpleCraftingRecipeSerializer<MShotBookRecipe>> MSHOT_BOOK_SERIALIZER =
            RECIPE_SERIALIZERS.register("mshot_book",
                    () -> new SimpleCraftingRecipeSerializer<>(MShotBookRecipe::new));

    public static final RegistryObject<SimpleCraftingRecipeSerializer<TerraBladeBookRecipe>> TERRA_BLADE_BOOK_SERIALIZER =
            RECIPE_SERIALIZERS.register("terra_blade_book",
                    () -> new SimpleCraftingRecipeSerializer<>(TerraBladeBookRecipe::new));
}
