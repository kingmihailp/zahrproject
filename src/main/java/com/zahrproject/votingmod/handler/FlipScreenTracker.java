package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.FlipScreenPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side tracker for the "Все вверх дном" screen-flip event.
 *
 * Stores the absolute expiry timestamp so that a player who reconnects
 * mid-effect is immediately told:
 *   1. to flip their screen (FlipScreenPacket(true))
 *   2. to show the remaining HUD timer (EventTimerPacket)
 */
public class FlipScreenTracker {

    /** Absolute expiry time in ms. 0 means the effect is not active. */
    private static volatile long flipExpiryMs = 0;

    /** Called when the flip event starts. */
    public static void setActive(long expiryMs) {
        flipExpiryMs = expiryMs;
    }

    /** Called when the flip event ends (FLIP_SCHEDULER fires). */
    public static void clear() {
        flipExpiryMs = 0;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now       = System.currentTimeMillis();
        long remaining = flipExpiryMs - now;
        if (remaining > 0) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new FlipScreenPacket(true));
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket("Все вверх дном", remaining));
        }
    }
}
