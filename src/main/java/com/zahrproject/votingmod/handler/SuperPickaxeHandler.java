package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the «Супер кирка» enchantment:
 *
 *   On block break, mines a 20×20 face aligned with the hit block.
 *   The pickaxe breaks after 3 uses (tracked via NBT, not vanilla durability).
 *   Indestructible blocks (bedrock etc.) are skipped.
 *
 *   A ThreadLocal recursion guard prevents the area-break loop from
 *   triggering this handler again for each sub-block.
 */
public class SuperPickaxeHandler {

    private static final String NBT_USES = "votingmod_superpick_uses";
    private static final int    MAX_USES = 3;

    /** Re-entry guard: prevents area-break loop from re-triggering the handler. */
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private static boolean hasSuperPickaxe(ItemStack stack) {
        return EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.SUPER_PICKAXE.get(), stack) > 0;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (ACTIVE.get()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ItemStack tool = player.getMainHandItem();
        if (!hasSuperPickaxe(tool)) return;

        // Cancel vanilla break — we handle the entire 20×20 area ourselves
        event.setCanceled(true);

        BlockPos center = event.getPos();
        ServerLevel level = player.serverLevel();
        Direction face = detectFace(player, center);

        ACTIVE.set(true);
        try {
            for (BlockPos pos : buildArea(center, face)) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (state.getDestroySpeed(level, pos) < 0) continue; // bedrock etc.
                level.destroyBlock(pos, true, player);
            }
        } finally {
            ACTIVE.set(false);
        }

        // Increment use counter; break pickaxe after MAX_USES
        int uses = tool.getOrCreateTag().getInt(NBT_USES) + 1;
        if (uses >= MAX_USES) {
            tool.hurtAndBreak(
                    tool.getMaxDamage() - tool.getDamageValue(),
                    player,
                    p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        } else {
            tool.getOrCreateTag().putInt(NBT_USES, uses);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Detect which face of the block the player is hitting by comparing
     * the player's eye position to the block center.
     */
    private static Direction detectFace(ServerPlayer player, BlockPos pos) {
        Vec3 diff = player.getEyePosition().subtract(Vec3.atCenterOf(pos));
        double ax = Math.abs(diff.x);
        double ay = Math.abs(diff.y);
        double az = Math.abs(diff.z);

        if (ay > ax && ay > az) return diff.y > 0 ? Direction.UP   : Direction.DOWN;
        if (ax > az)            return diff.x > 0 ? Direction.EAST  : Direction.WEST;
        return                         diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Return a 20×20 list of positions on the plane defined by {@code face},
     * centered on {@code center} (offset -9 to +10 in each spanning axis).
     */
    private static List<BlockPos> buildArea(BlockPos center, Direction face) {
        List<BlockPos> list = new ArrayList<>(400);
        Direction.Axis normal = face.getAxis();

        for (int a = -9; a <= 10; a++) {
            for (int b = -9; b <= 10; b++) {
                list.add(switch (normal) {
                    case Y -> center.offset(a, 0, b);
                    case X -> center.offset(0, a, b);
                    case Z -> center.offset(a, b, 0);
                });
            }
        }
        return list;
    }
}
