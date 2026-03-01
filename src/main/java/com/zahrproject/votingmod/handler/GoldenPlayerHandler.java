package com.zahrproject.votingmod.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GoldenPlayerHandler {

    /** UUID → expiry timestamp in milliseconds */
    private static final Map<UUID, Long> goldenPlayers = new ConcurrentHashMap<>();

    private static final Set<Item> EXEMPT_ITEMS = Set.of(
            Items.GOLDEN_CARROT,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.GOLDEN_HELMET,
            Items.GOLDEN_CHESTPLATE,
            Items.GOLDEN_LEGGINGS,
            Items.GOLDEN_BOOTS,
            Items.GOLDEN_SWORD,
            Items.GOLDEN_PICKAXE,
            Items.GOLDEN_AXE,
            Items.GOLDEN_SHOVEL,
            Items.GOLDEN_HOE,
            Items.GOLD_INGOT,
            Items.GOLD_BLOCK,
            Items.GOLD_NUGGET,
            Items.RAW_GOLD,
            Items.RAW_GOLD_BLOCK,
            Items.GOLDEN_HORSE_ARMOR,
            Items.BELL
    );

    /**
     * Makes the given player "golden" for durationMs milliseconds.
     * Immediately replaces non-exempt items in both hands with gold ingots.
     */
    public static void makeGolden(ServerPlayer player, long durationMs) {
        goldenPlayers.put(player.getUUID(), System.currentTimeMillis() + durationMs);
        replaceHandItem(player, InteractionHand.MAIN_HAND);
        replaceHandItem(player, InteractionHand.OFF_HAND);
    }

    private static void replaceHandItem(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && !EXEMPT_ITEMS.contains(stack.getItem())) {
            player.setItemInHand(hand, new ItemStack(Items.GOLD_INGOT, stack.getCount()));
        }
    }

    public static boolean isGolden(UUID uuid) {
        Long expiry = goldenPlayers.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            goldenPlayers.remove(uuid);
            return false;
        }
        return true;
    }

    // ── Gold trail ────────────────────────────────────────────────────────────

    /** Every 4 ticks, turn the block under the golden player into gold. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!isGolden(player.getUUID())) return;
        if (player.tickCount % 4 != 0) return;

        BlockPos below = player.blockPosition().below();
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(below);
        if (!state.isAir() && !state.is(Blocks.WATER) && !state.is(Blocks.GOLD_BLOCK)) {
            level.setBlock(below, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        }
    }

    // ── Block break → replace with gold ──────────────────────────────────────

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!isGolden(player.getUUID())) return;
        event.setCanceled(true);
        player.serverLevel().setBlock(event.getPos(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
    }

    // ── Block use → replace with gold ────────────────────────────────────────

    @SubscribeEvent
    public void onBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isGolden(player.getUUID())) return;
        event.setCanceled(true);
        player.serverLevel().setBlock(event.getPos(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
    }
}
