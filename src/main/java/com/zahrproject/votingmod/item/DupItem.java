package com.zahrproject.votingmod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * «Дюп» — редкий предмет, выпадающий при ломании любого блока с шансом 0.0001%.
 *
 * Внешний вид: всегда отображается с эффектом переливающегося зачарования (isFoil = true),
 * что делает его похожим на переливающиеся текстуры из игры.
 *
 * Функционал: если положить в сетку крафта с чем угодно, удваивает результат
 * и сам расходуется — логика реализована в {@link com.zahrproject.votingmod.recipe.DupRecipe}.
 */
public class DupItem extends Item {

    public DupItem(Properties properties) {
        super(properties);
    }

    /** Always show the enchantment glint / foil shimmer, regardless of enchantments. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.votingmod.dup.desc"));
    }
}
