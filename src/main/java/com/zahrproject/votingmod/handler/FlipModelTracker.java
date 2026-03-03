package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.FlipModelPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side tracker for the "Голова вниз" player-model-flip event.
 *
 * On login the client always receives the current flip state so that:
 *  - If the effect is still running → models are rendered upside-down + HUD timer.
 *  - If the effect ended while offline → FlipModelPacket(false) resets the client flag.
 */
public class FlipModelTracker {

    public static final String TIMER_NAME = "Голова вниз";

    private static volatile long flipModelExpiryMs   = 0;
    private static volatile long flipModelDurationMs = 0;

    public static void setActive(long expiryMs, long durationMs) {
        flipModelExpiryMs   = expiryMs;
        flipModelDurationMs = durationMs;
    }

    public static void clear() {
        flipModelExpiryMs   = 0;
        flipModelDurationMs = 0;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long remaining = flipModelExpiryMs - System.currentTimeMillis();
        boolean active = remaining > 0;

        // Always send so the client's static flag is never left in a stale state.
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FlipModelPacket(active));

        if (active) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, remaining, flipModelDurationMs));
        }
    }
}
