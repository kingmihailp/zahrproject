package com.zahrproject.votingmod.recipe;

import com.zahrproject.votingmod.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * «Дюп» в крафте: если один экземпляр {@link ModItems#DUP} присутствует
 * в сетке крафта вместе с любым другим рецептом, результат того рецепта
 * удваивается, а сам Дюп расходуется.
 *
 * Алгоритм:
 *  1. Найти ячейку с Дюпом.
 *  2. Создать копию контейнера без Дюпа.
 *  3. Найти любой подходящий крафтовый рецепт для этой копии.
 *  4. Вернуть удвоенный результат того рецепта.
 */
public class DupRecipe extends CustomRecipe {

    /**
     * Stores the base recipe found during the last {@link #matches} call,
     * per thread, so {@link #assemble} can reuse it without a Level reference.
     */
    private final ThreadLocal<RecipeHolder<CraftingRecipe>> cachedBase = new ThreadLocal<>();

    public DupRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int dupSlot = findDupSlot(container);
        if (dupSlot < 0) return false;

        CraftingContainer copy = copyWithoutSlot(container, dupSlot);

        Optional<RecipeHolder<CraftingRecipe>> base =
                level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, copy, level);

        if (base.isEmpty()) return false;

        // Guard against infinite recursion with our own recipe
        if (base.get().value() instanceof DupRecipe) return false;

        cachedBase.set(base.get());
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        RecipeHolder<CraftingRecipe> base = cachedBase.get();
        if (base == null) return ItemStack.EMPTY;

        int dupSlot = findDupSlot(container);
        if (dupSlot < 0) return ItemStack.EMPTY;

        CraftingContainer copy = copyWithoutSlot(container, dupSlot);
        ItemStack result = base.value().assemble(copy, registryAccess).copy();

        if (result.isEmpty()) return ItemStack.EMPTY;

        // Double the result, clamping to the item's max stack size
        result.setCount(Math.min(result.getCount() * 2, result.getMaxStackSize()));
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        RecipeHolder<CraftingRecipe> base = cachedBase.get();
        int dupSlot = findDupSlot(container);

        if (base == null || dupSlot < 0) {
            return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        }

        // Ask the base recipe what it would leave behind (buckets, bottles, etc.)
        CraftingContainer copy = copyWithoutSlot(container, dupSlot);
        NonNullList<ItemStack> remaining = base.value().getRemainingItems(copy);
        // The DUP slot in the copy is empty so its remaining item is also empty — correct.
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        // Need at least 2 slots: one for the DUP, one for the ingredient
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DUP_SERIALIZER.get();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Returns the index of the first DUP item in the container, or -1. */
    private int findDupSlot(CraftingContainer container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(ModItems.DUP.get())) return i;
        }
        return -1;
    }

    /**
     * Creates a new {@link CraftingContainer} (with a null menu — safe because
     * {@code CraftingContainer.setChanged()} guards against a null menu)
     * that is a copy of {@code src} with slot {@code skipSlot} left empty.
     */
    private static CraftingContainer copyWithoutSlot(CraftingContainer src, int skipSlot) {
        CraftingContainer copy = new CraftingContainer(null, src.getWidth(), src.getHeight());
        for (int i = 0; i < src.getContainerSize(); i++) {
            if (i != skipSlot) {
                copy.setItem(i, src.getItem(i).copy());
            }
        }
        return copy;
    }
}
