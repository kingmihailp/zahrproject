package com.zahrproject.votingmod.recipe;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;

/**
 * Крафт «M-выстрел»:
 *
 *   [Арбалет][Арбалет][Арбалет]
 *   [Арбалет][Книга с X-выстрелом][Арбалет]
 *   [Арбалет][Арбалет][Арбалет]
 *
 * Результат: зачарованная книга с «M-выстрелом».
 */
public class MShotBookRecipe extends CustomRecipe {

    public MShotBookRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (container.getWidth() != 3 || container.getHeight() != 3) return false;

        // Center slot (index 4) must be an enchanted book with X-shot
        ItemStack center = container.getItem(4);
        if (!center.is(Items.ENCHANTED_BOOK)) return false;
        if (!hasXShotStored(center)) return false;

        // All other 8 slots must be crossbows
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            if (!container.getItem(i).is(Items.CROSSBOW)) return false;
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(result,
                new EnchantmentInstance(ModEnchantments.MSHOT.get(), 1));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MSHOT_BOOK_SERIALIZER.get();
    }

    /** Check that the enchanted book has X-shot in its StoredEnchantments tag. */
    private static boolean hasXShotStored(ItemStack book) {
        return EnchantmentHelper.deserializeEnchantments(EnchantedBookItem.getEnchantments(book))
                .containsKey(ModEnchantments.XSHOT.get());
    }
}
