package com.zahrproject.votingmod.enchantments;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Супер кирка: при ударе по блоку ломает область 20×20 на той же грани.
 * Ломается после трёх ударов. Только через книгу, на любую кирку.
 */
public class SuperPickaxeEnchantment extends Enchantment {

    public static final EnchantmentCategory PICKAXE_CATEGORY =
            EnchantmentCategory.create("votingmod_pickaxe",
                    item -> item instanceof PickaxeItem);

    public SuperPickaxeEnchantment() {
        super(Rarity.VERY_RARE, PICKAXE_CATEGORY, new EquipmentSlot[]{ EquipmentSlot.MAINHAND });
    }

    @Override public int getMaxLevel() { return 1; }
    @Override public boolean isTreasureOnly() { return true; }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }
}
