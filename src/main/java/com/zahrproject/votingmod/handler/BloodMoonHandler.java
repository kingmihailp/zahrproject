package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Истинный хардкор" event:
 *
 *   • All players' max health is reduced to 1.0 HP (half a heart).
 *   • After 5 minutes everything reverts to the default 20.0 HP (10 hearts).
 */
public class BloodMoonHandler {

    public static final String TIMER_NAME = "Истинный хардкор";

    private static final String NBT_EXPIRY_KEY   = "votingmod_true_hardcore_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_true_hardcore_duration";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-TrueHardcoreRevert");
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
            CompoundTag tag = p.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
            applyHealthReduction(p);
        }

        EventTimerPacket timerStart = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);

        scheduleRevert(duration);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(NBT_EXPIRY_KEY)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        long savedExpiry   = tag.getLong(NBT_EXPIRY_KEY);
        long savedDuration = tag.getLong(NBT_DURATION_KEY);
        long remaining     = savedExpiry - System.currentTimeMillis();

        if (remaining <= 0) {
            // Expired while offline — restore health and clear keys
            restorePlayerHealth(player);
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
            expiryMs = 0;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            scheduleRevert(remaining);
        }

        applyHealthReduction(player);

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EventTimerPacket(TIMER_NAME, remaining, savedDuration));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isActive()) return;
        // Death-respawn resets attribute base values — reapply the reduction.
        applyHealthReduction(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag tag = player.getPersistentData();
        if (isActive()) {
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        } else {
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Reduce max health to 1.0 HP (half a heart), capping current HP. */
    private static void applyHealthReduction(ServerPlayer player) {
        var attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(1.0);
        if (player.getHealth() > 1.0f) {
            player.setHealth(1.0f);
        }
    }

    /** Restore max health to the Minecraft default (20 HP = 10 hearts). */
    private static void restorePlayerHealth(ServerPlayer player) {
        var attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0);
        }
    }

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            // Set expiryMs = 0 INSIDE the server-thread task so that
            // onPlayerLogout cannot race between "expiryMs=0" and the restore
            // loop and accidentally clear the NBT keys before we restore health.
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) {
                expiryMs = 0;
                return;
            }
            srv.execute(() -> {
                expiryMs = 0; // now safe: we're on the main thread, logout is also main-thread
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    restorePlayerHealth(p);
                    CompoundTag tag = p.getPersistentData();
                    tag.remove(NBT_EXPIRY_KEY);
                    tag.remove(NBT_DURATION_KEY);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
