package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.RandomTexturePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side tracker for the "Это точно не вирус?" texture-shuffle event.
 * Mirrors the pattern of {@link FlipScreenTracker}.
 */
public class RandomTextureTracker {

    private static volatile long expiryMs   = 0;
    private static volatile long durationMs = 0;

    public static void setActive(long expiryMs, long durationMs) {
        RandomTextureTracker.expiryMs   = expiryMs;
        RandomTextureTracker.durationMs = durationMs;
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
                new RandomTexturePacket(active));

        if (active) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket("Это точно не вирус?", remaining, durationMs));
        }
    }
}
