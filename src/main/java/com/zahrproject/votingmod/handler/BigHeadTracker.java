package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.BigHeadPacket;
import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side tracker for the "Режим большой головы" voting event.
 *
 * Stores the absolute expiry timestamp so that a reconnecting player
 * receives the correct state.
 */
public class BigHeadTracker {

    public static final String TIMER_NAME = "Режим большой головы";

    private static volatile long expiryMs   = 0;
    private static volatile long durationMs = 0;

    public static void setActive(long expiry, long duration) {
        expiryMs   = expiry;
        durationMs = duration;
    }

    public static void clear() {
        expiryMs   = 0;
        durationMs = 0;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long remaining = expiryMs - System.currentTimeMillis();
        boolean active = remaining > 0;

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BigHeadPacket(active));

        if (active) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, remaining, durationMs));
        }
    }
}
