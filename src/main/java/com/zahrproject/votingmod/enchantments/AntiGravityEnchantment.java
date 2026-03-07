package com.zahrproject.votingmod.enchantments;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Антигравитация: уменьшает гравитацию игрока в 1,5 раза за уровень.
 *  • Уровень 1 — гравитация × (1/1.5) ≈ 0.67
 *  • Уровень 2 — гравитация × (1/3.0) ≈ 0.33
 *  • Уровень 3 — гравитация × (1/4.5) ≈ 0.22
 * Накладывается только на ботинки, только через книгу,
 * совместима со всеми другими зачарованиями.
 */
public class AntiGravityEnchantment extends Enchantment {

    public AntiGravityEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR_FEET,
                new EquipmentSlot[]{ EquipmentSlot.FEET });
    }

    @Override public int getMaxLevel() { return 3; }

    @Override public int getMinCost(int level) { return 10 + (level - 1) * 10; }
    @Override public int getMaxCost(int level) { return getMinCost(level) + 30; }

    @Override public boolean isTreasureOnly() { return true; }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return EnchantmentCategory.ARMOR_FEET.canEnchant(stack.getItem());
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }

    /** Compatible with everything — no conflicts. */
    @Override
    protected boolean checkCompatibility(Enchantment other) { return true; }
}
