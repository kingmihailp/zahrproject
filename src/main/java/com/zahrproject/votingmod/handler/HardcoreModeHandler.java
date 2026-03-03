package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Выше, сильнее, сложнее" event:
 *  - All players' max health is reduced to 4 HP (2 hearts) for the duration.
 *  - Health reduction and timer survive reconnects via player NBT.
 *  - On expiry (whether online or offline) max health is restored to 20 HP.
 */
public class HardcoreModeHandler {

    public static final String TIMER_NAME = "Выше, сильнее, сложнее";

    private static final String NBT_EXPIRY_KEY   = "votingmod_hardcore_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_hardcore_duration";
    /** Set to true when we reduced this player's max health, so we can restore it on login. */
    private static final String NBT_HEALTH_KEY   = "votingmod_hardcore_health_reduced";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-HardcoreRevert");
                t.setDaemon(true);
                return t;
            });

    public static volatile long expiryMs   = 0;
    public static volatile long durationMs = 0;

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server, long duration) {
        durationMs = duration;
        expiryMs   = System.currentTimeMillis() + duration;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            applyHardcoreHealth(p);
            p.getPersistentData().putLong(NBT_EXPIRY_KEY,   expiryMs);
            p.getPersistentData().putLong(NBT_DURATION_KEY, durationMs);
        }

        EventTimerPacket timerStart = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);

        SCHEDULER.schedule(() -> {
            expiryMs = 0;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    revertHardcoreHealth(p);
                    p.getPersistentData().remove(NBT_EXPIRY_KEY);
                    p.getPersistentData().remove(NBT_DURATION_KEY);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, duration, TimeUnit.MILLISECONDS);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        CompoundTag tag = player.getPersistentData();

        // Health was reduced in a previous session; effect might have ended while offline.
        if (tag.getBoolean(NBT_HEALTH_KEY) && !tag.contains(NBT_EXPIRY_KEY)) {
            // No active event recorded but health flag is set → restore unconditionally.
            server.execute(() -> revertHardcoreHealth(player));
            return;
        }

        if (!tag.contains(NBT_EXPIRY_KEY)) {
            // Not part of any event → clear stale HUD bar if present.
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        long savedExpiry   = tag.getLong(NBT_EXPIRY_KEY);
        long savedDuration = tag.getLong(NBT_DURATION_KEY);
        long remaining     = savedExpiry - System.currentTimeMillis();

        if (remaining <= 0) {
            // Effect expired while offline.
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
            expiryMs = 0;
            server.execute(() -> revertHardcoreHealth(player));
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        // Still active — restore global state if lost (same-JVM world re-enter or server restart).
        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            SCHEDULER.schedule(() -> {
                expiryMs = 0;
                MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
                if (srv == null) return;
                srv.execute(() -> {
                    for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                        revertHardcoreHealth(p);
                        p.getPersistentData().remove(NBT_EXPIRY_KEY);
                        p.getPersistentData().remove(NBT_DURATION_KEY);
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                                new EventTimerPacket(TIMER_NAME, 0, 0));
                    }
                });
            }, remaining, TimeUnit.MILLISECONDS);
        }

        // Apply reduced health and re-send HUD timer.
        server.execute(() -> applyHardcoreHealth(player));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EventTimerPacket(TIMER_NAME, remaining, savedDuration));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isActive()) {
            CompoundTag tag = player.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        } else {
            player.getPersistentData().remove(NBT_EXPIRY_KEY);
            player.getPersistentData().remove(NBT_DURATION_KEY);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void applyHardcoreHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(4.0); // 2 hearts
        if (player.getHealth() > 4.0f) player.setHealth(4.0f);
        player.getPersistentData().putBoolean(NBT_HEALTH_KEY, true);
    }

    private static void revertHardcoreHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(20.0); // 10 hearts
        player.getPersistentData().remove(NBT_HEALTH_KEY);
    }
}
