package com.zahrproject.votingmod.enchantments;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * X-выстрел: арбалет стреляет 10 стрелами сразу с небольшим разбросом.
 * Время зарядки удваивается. Несовместим с «Тройным выстрелом».
 * Требует 10 стрел (или фейерверков) нужного типа в инвентаре/второй руке.
 */
public class XShotEnchantment extends Enchantment {

    /** Custom category that accepts only crossbows. */
    public static final EnchantmentCategory CROSSBOW_CATEGORY =
            EnchantmentCategory.create("votingmod_crossbow",
                    item -> item == Items.CROSSBOW);

    public XShotEnchantment() {
        super(Rarity.RARE, CROSSBOW_CATEGORY, new EquipmentSlot[]{ EquipmentSlot.MAINHAND });
    }

    @Override public int getMaxLevel() { return 1; }

    @Override public int getMinCost(int level) { return 20; }
    @Override public int getMaxCost(int level) { return 50; }

    @Override
    public boolean isTreasureOnly() { return true; }

    @Override
    public boolean canEnchant(ItemStack stack) { return stack.getItem() == Items.CROSSBOW; }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other) && other != Enchantments.MULTISHOT;
    }
}
