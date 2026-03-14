package com.zahrproject.votingmod.recipe;

import com.zahrproject.votingmod.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * «Дюп» в крафте: если один экземпляр {@link ModItems#DUP} присутствует
 * в сетке крафта вместе с одинаковыми предметами, все эти предметы
 * удваиваются, а сам Дюп расходуется.
 *
 * Алгоритм:
 *  1. Найти ячейку с Дюпом.
 *  2. Убедиться, что остальные непустые ячейки содержат один и тот же предмет.
 *  3. Суммировать количество этих предметов и вернуть удвоенное значение.
 *  4. Потребить все исходные предметы (не только по 1 из ячейки).
 */
public class DupRecipe extends CustomRecipe {

    public DupRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int dupSlot = findDupSlot(container);
        if (dupSlot < 0) return false;

        ItemStack target = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (i == dupSlot) continue;
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (target.isEmpty()) {
                target = stack;
            } else if (!ItemStack.isSameItemSameTags(target, stack)) {
                // Разные предметы — не поддерживается
                return false;
            }
        }

        return !target.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        int dupSlot = findDupSlot(container);
        if (dupSlot < 0) return ItemStack.EMPTY;

        ItemStack target = ItemStack.EMPTY;
        int totalCount = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (i == dupSlot) continue;
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (target.isEmpty()) target = stack;
            totalCount += stack.getCount();
        }

        if (target.isEmpty()) return ItemStack.EMPTY;

        // Потребить все лишние предметы (оставить по 1 в каждой ячейке — их
        // уберёт стандартная механика крафта)
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (i == dupSlot) continue;
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.getCount() > 1) {
                container.removeItem(i, stack.getCount() - 1);
            }
        }

        ItemStack result = target.copy();
        result.setCount(Math.min(totalCount * 2, result.getMaxStackSize()));
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        // Стандартная механика уберёт по 1 предмету из каждой ячейки;
        // ничего дополнительно возвращать не нужно
        return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        // Нужно минимум 2 ячейки: одна для Дюпа, одна для предмета
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DUP_SERIALIZER.get();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Возвращает индекс первого слота с Дюпом, или -1. */
    private int findDupSlot(CraftingContainer container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(ModItems.DUP.get())) return i;
        }
        return -1;
    }
}
