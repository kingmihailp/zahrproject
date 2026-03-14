package com.zahrproject.votingmod.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side handler for the "Переключить ПлохоеВремя" voting event.
 *
 * When active, intercepts bed-sleep attempts in bed-friendly dimensions
 * (Overworld and similar) and causes an explosion instead of sleeping.
 * In Nether/End, beds already explode via vanilla BedBlock logic.
 *
 * The state is a simple toggle: each vote flips active ↔ inactive.
 */
public class BadTimeHandler {

    private static volatile boolean active = false;

    /**
     * Toggles the bad-time state and returns the new state.
     * true  = beds now explode everywhere
     * false = beds work normally again
     */
    public static boolean toggle() {
        active = !active;
        return active;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Fires when a player tries to sleep in a bed (only in dimensions where
     * beds are allowed, i.e. the Overworld). Nether/End beds explode before
     * this event fires via vanilla BedBlock code.
     */
    @SubscribeEvent
    public static void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if (!active) return;
        Level level = event.getEntity().level();
        if (level.isClientSide()) return;

        // Cancel the sleep attempt
        event.setResult(Player.SleepResult.OTHER_PROBLEM);

        // Explode at the bed position
        BlockPos pos = event.getPos();
        level.explode(
                null,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                5.0f,
                Level.ExplosionInteraction.BLOCK
        );
    }
}
