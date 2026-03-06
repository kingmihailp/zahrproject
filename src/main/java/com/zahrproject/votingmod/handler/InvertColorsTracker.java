package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.InvertColorsPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side tracker for the "Взгляд эндермэна" colour-inversion event.
 *
 * Mirrors the pattern of {@link FlipScreenTracker}: stores absolute expiry +
 * total duration so reconnecting players receive correct state.
 */
public class InvertColorsTracker {

    private static volatile long expiryMs   = 0;
    private static volatile long durationMs = 0;

    public static void setActive(long expiryMs, long durationMs) {
        InvertColorsTracker.expiryMs   = expiryMs;
        InvertColorsTracker.durationMs = durationMs;
    }

    public static void clear() {
        expiryMs   = 0;
        durationMs = 0;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now       = System.currentTimeMillis();
        long remaining = expiryMs - now;
        boolean active = remaining > 0;

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new InvertColorsPacket(active));

        if (active) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket("Взгляд эндермэна", remaining, durationMs));
        }
    }
}
