package com.zahrproject.votingmod.enchantments;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Base for all elemental blade enchantments.
 * Compatible with swords and axes; does not conflict with any other enchantment.
 * Only applicable via enchanted book (treasure only, no enchanting table).
 */
public abstract class BladeEnchantment extends Enchantment {

    public static final EnchantmentCategory BLADE_CATEGORY =
            EnchantmentCategory.create("votingmod_blade",
                    item -> item instanceof SwordItem || item instanceof AxeItem);

    protected BladeEnchantment() {
        super(Rarity.RARE, BLADE_CATEGORY, new EquipmentSlot[]{ EquipmentSlot.MAINHAND });
    }

    @Override public int getMaxLevel() { return 1; }
    @Override public int getMinCost(int level) { return 20; }
    @Override public int getMaxCost(int level) { return 50; }
    @Override public boolean isTreasureOnly() { return true; }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return BLADE_CATEGORY.canEnchant(stack.getItem());
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }

    /** Compatible with everything — no conflicts. */
    @Override
    protected boolean checkCompatibility(Enchantment other) { return true; }
}
