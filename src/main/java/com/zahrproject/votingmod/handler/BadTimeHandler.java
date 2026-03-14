package com.zahrproject.votingmod.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side handler for the "Переключить ПлохоеВремя" voting event.
 *
 * When active, intercepts bed-sleep attempts in bed-friendly dimensions
 * (Overworld and similar) and causes a vanilla-identical explosion:
 *   1. Both halves of the bed are removed.
 *   2. Level.explode() is called with the same parameters vanilla uses in
 *      Nether/End (badRespawnPointExplosion, power 5, fire=true).
 * In Nether/End, beds explode via vanilla BedBlock logic before this event fires.
 *
 * The state is a simple toggle: each vote flips active ↔ inactive.
 *
 * Note: PlayerSleepInBedEvent always receives the HEAD block position because
 * BedBlock.use() normalises the pos to the HEAD before calling startSleepInBed().
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
     * Fires when a player tries to sleep in a bed in a dimension where beds
     * work (i.e. the Overworld). Mirrors the vanilla Nether/End explosion
     * from BedBlock.use() exactly.
     */
    @SubscribeEvent
    public static void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if (!active) return;
        Level level = event.getEntity().level();
        if (level.isClientSide()) return;

        // Cancel the sleep attempt
        event.setResult(Player.SleepResult.OTHER_PROBLEM);

        // pos is the HEAD block (BedBlock.use() normalises before firing the event)
        BlockPos pos = event.getPos();
        BlockState bedState = level.getBlockState(pos);
        Vec3 center = Vec3.atCenterOf(pos);

        // Remove the HEAD block
        level.removeBlock(pos, false);

        // Remove the FOOT block — same logic as vanilla BedBlock.use()
        if (bedState.getBlock() instanceof BedBlock) {
            BlockPos footPos = pos.relative(bedState.getValue(BedBlock.FACING).getOpposite());
            if (level.getBlockState(footPos).is(bedState.getBlock())) {
                level.removeBlock(footPos, false);
            }
        }

        // Explode exactly as vanilla does for Nether/End beds:
        // null entity, badRespawnPointExplosion damage source, null calculator,
        // power 5.0, fire = true, BLOCK interaction.
        level.explode(
                null,
                level.damageSources().badRespawnPointExplosion(center),
                null,
                center.x, center.y, center.z,
                5.0f, true,
                Level.ExplosionInteraction.BLOCK
        );
    }
}
