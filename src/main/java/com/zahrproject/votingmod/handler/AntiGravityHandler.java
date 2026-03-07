package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Applies a gravity-reduction attribute modifier to any server-side player
 * wearing boots enchanted with Антигравитация.
 *
 * The modifier is transient (not saved to NBT) so it is re-applied
 * every 10 ticks and disappears naturally if the boots are removed.
 *
 * Gravity formula: default 0.08 blocks/tick²
 *   Level 1 → 0.08 / 1.5 ≈ 0.053
 *   Level 2 → 0.08 / 3.0 ≈ 0.027
 *   Level 3 → 0.08 / 4.5 ≈ 0.018
 *
 * Implemented via MULTIPLY_TOTAL:  finalValue = base × (1 + modifier)
 *   modifier = 1/(1.5 × level) − 1   (always negative)
 */
public class AntiGravityHandler {

    private static final UUID   MODIFIER_UUID = UUID.fromString("c7a1f3e2-4b8d-4f91-a2c6-1d3e5f7a9b0c");
    private static final String MODIFIER_NAME = "votingmod_anti_gravity";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        // Re-check every 10 ticks — responsive enough, low overhead
        if (player.tickCount % 10 != 0) return;

        var attr = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (attr == null) return;

        // Always remove first so we can cleanly re-apply (or leave removed)
        attr.removeModifier(MODIFIER_UUID);

        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.ANTI_GRAVITY.get(), boots);

        if (level > 0) {
            double divisor  = 1.5 * level;             // 1.5, 3.0, 4.5
            double modifier = (1.0 / divisor) - 1.0;   // e.g. -0.333 at level 1
            attr.addTransientModifier(new AttributeModifier(
                    MODIFIER_UUID, MODIFIER_NAME,
                    modifier, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}
