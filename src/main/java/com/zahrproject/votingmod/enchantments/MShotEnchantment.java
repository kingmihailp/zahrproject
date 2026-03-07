package com.zahrproject.votingmod.enchantments;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * M-выстрел: арбалет стреляет 100 стрелами сразу с небольшим разбросом.
 * Время зарядки в 5 раз дольше обычного. Несовместим с «Тройным выстрелом»
 * и «X-выстрелом». Получается только через крафт из книги с X-выстрелом.
 */
public class MShotEnchantment extends Enchantment {

    public MShotEnchantment() {
        super(Rarity.VERY_RARE,
                XShotEnchantment.CROSSBOW_CATEGORY,
                new EquipmentSlot[]{ EquipmentSlot.MAINHAND });
    }

    @Override public int getMaxLevel() { return 1; }

    @Override public int getMinCost(int level) { return 30; }
    @Override public int getMaxCost(int level) { return 60; }

    @Override
    public boolean isTreasureOnly() { return true; }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other)
                && other != Enchantments.MULTISHOT
                && other != ModEnchantments.XSHOT.get();
    }
}
