package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Подрывашкер" event:
 *
 *   • Every non-player mob that dies leaves a primed TNT block (4-second fuse)
 *     at its death location for the duration of the event.
 *   • Lasts 3 minutes.  Timer survives server restarts and player re-logins
 *     via NBT persistence (same pattern as HardcoreModeHandler).
 */
public class BomberHandler {

    public static final String TIMER_NAME = "Подрывашкер";

    private static final String NBT_EXPIRY_KEY   = "votingmod_bomber_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_bomber_duration";

    private static final long DURATION_MS = 3 * 60 * 1000L; // 3 minutes

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-BomberRevert");
                t.setDaemon(true);
                return t;
            });

    public static volatile long expiryMs   = 0;
    public static volatile long durationMs = 0;

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server) {
        durationMs = DURATION_MS;
        expiryMs   = System.currentTimeMillis() + durationMs;

        // Persist to all currently-online players so the state survives restarts.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CompoundTag tag = p.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        }

        // Show HUD timer on all clients.
        EventTimerPacket timerStart = new EventTimerPacket(TIMER_NAME, durationMs, durationMs);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);

        scheduleRevert(durationMs);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    /** Spawn a primed TNT block at every killed non-player mob while active. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!isActive()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer) return; // skip player deaths
        if (!(entity.level() instanceof ServerLevel level)) return;

        PrimedTnt tnt = new PrimedTnt(level,
                entity.getX(), entity.getY(), entity.getZ(), null);
        tnt.setFuse(80); // 4-second fuse (80 ticks)
        level.addFreshEntity(tnt);
    }

    /** Restore HUD timer on player login; re-activate global state after restart. */
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
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
            expiryMs = 0;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        // Re-activate global state after a server restart.
        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            scheduleRevert(remaining);
        }

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EventTimerPacket(TIMER_NAME, remaining, savedDuration));
    }

    /** Persist the expiry to NBT on player logout so it survives re-logins. */
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

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            expiryMs = 0;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    p.getPersistentData().remove(NBT_EXPIRY_KEY);
                    p.getPersistentData().remove(NBT_DURATION_KEY);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
