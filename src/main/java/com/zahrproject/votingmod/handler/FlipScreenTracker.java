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
 * Stores the absolute expiry timestamp AND total duration so that a player
 * who reconnects receives correct state regardless of when they left:
 *   • effect still active  → FlipScreenPacket(true) + timer with remaining time
 *   • effect already ended → FlipScreenPacket(false) + remove stale timer
 *
 * Without the "already ended" case the client's static {@code flipped} boolean
 * would never be reset and the screen would stay upside-down permanently.
 */
public class FlipScreenTracker {

    /** Absolute expiry time in ms. 0 means the effect is not active. */
    private static volatile long flipExpiryMs    = 0;
    /** Total duration of the event in ms (for HUD bar fraction on reconnect). */
    private static volatile long flipDurationMs  = 0;

    /** Called when the flip event starts. */
    public static void setActive(long expiryMs, long durationMs) {
        flipExpiryMs   = expiryMs;
        flipDurationMs = durationMs;
    }

    /** Called when the flip event ends (FLIP_SCHEDULER fires). */
    public static void clear() {
        flipExpiryMs  = 0;
        flipDurationMs = 0;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now       = System.currentTimeMillis();
        long remaining = flipExpiryMs - now;
        boolean active = remaining > 0;

        // ALWAYS send the current flip state.
        // If we only sent on active=true, the client's static `flipped` boolean
        // would never be reset when the effect expired while the player was offline.
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FlipScreenPacket(active));

        if (active) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket("Все вверх дном", remaining, flipDurationMs));
        }
        // If not active: EventTimerHud.activeTimers was already cleared by the
        // LoggingOut handler on the client side, so no explicit "remove" packet needed.
    }
}
