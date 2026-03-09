package com.zahrproject.votingmod.recipe;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * Бесформенный крафт «Терра-лезвие»:
 *   Лезвие воды (книга) + Лезвие огня (книга) + Лезвие земли (книга)
 *   + Лезвие ветра (книга) + Осколок эха
 *   = зачарованная книга «Терра-лезвие»
 */
public class TerraBladeBookRecipe extends CustomRecipe {

    public TerraBladeBookRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        boolean hasWater = false, hasFire = false, hasEarth = false, hasWind = false, hasEcho = false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(Items.ECHO_SHARD)) {
                if (hasEcho) return false; // duplicate
                hasEcho = true;
                continue;
            }

            if (stack.is(Items.ENCHANTED_BOOK)) {
                Map<Enchantment, Integer> enchants =
                        EnchantmentHelper.deserializeEnchantments(EnchantedBookItem.getEnchantments(stack));

                if (enchants.containsKey(ModEnchantments.WATER_BLADE.get())) {
                    if (hasWater) return false;
                    hasWater = true;
                } else if (enchants.containsKey(ModEnchantments.FIRE_BLADE.get())) {
                    if (hasFire) return false;
                    hasFire = true;
                } else if (enchants.containsKey(ModEnchantments.EARTH_BLADE.get())) {
                    if (hasEarth) return false;
                    hasEarth = true;
                } else if (enchants.containsKey(ModEnchantments.WIND_BLADE.get())) {
                    if (hasWind) return false;
                    hasWind = true;
                } else {
                    return false; // unknown enchanted book
                }
                continue;
            }

            return false; // unrecognised item
        }

        return hasWater && hasFire && hasEarth && hasWind && hasEcho;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(result,
                new EnchantmentInstance(ModEnchantments.TERRA_BLADE.get(), 1));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 5;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TERRA_BLADE_BOOK_SERIALIZER.get();
    }
}
