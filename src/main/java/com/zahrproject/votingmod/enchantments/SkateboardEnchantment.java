package com.zahrproject.votingmod.enchantments;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class SkateboardEnchantment extends Enchantment {

    /** Custom category that accepts only shields. */
    public static final EnchantmentCategory SHIELD_CATEGORY =
            EnchantmentCategory.create("votingmod_shield", item -> item == Items.SHIELD);

    public SkateboardEnchantment() {
        super(Rarity.RARE, SHIELD_CATEGORY, new EquipmentSlot[]{ EquipmentSlot.OFFHAND });
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() == Items.SHIELD;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return false; // Only obtainable via enchanted book
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }
}
