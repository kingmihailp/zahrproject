package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.item.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Random;

/**
 * General Forge event handler.
 */
public class ForgeEventHandler {

    private static final Random RANDOM = new Random();

    /**
     * Drop chance for the «Дюп» item: 0.0001 % = 1 in 1 000 000.
     */
    private static final double DUP_DROP_CHANCE = 0.000001;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // When a player joins, nothing special needed.
        // VotingManager already picks up online players dynamically at vote time.
    }

    /**
     * After any block is broken by a player, roll for a «Дюп» drop.
     * Chance: 0.0001% (1 in 1 000 000).
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (RANDOM.nextDouble() >= DUP_DROP_CHANCE) return;

        ItemStack dup = new ItemStack(ModItems.DUP.get());
        double x = event.getPos().getX() + 0.5;
        double y = event.getPos().getY() + 0.5;
        double z = event.getPos().getZ() + 0.5;

        ItemEntity itemEntity = new ItemEntity(
                (net.minecraft.world.level.Level) event.getLevel(),
                x, y, z, dup
        );
        event.getLevel().addFreshEntity(itemEntity);
    }
}
