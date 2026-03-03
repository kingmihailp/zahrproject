package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Выше, сильнее, сложнее" event — real Minecraft hardcore mode for a limited time:
 *
 *   • World difficulty is set to HARD.
 *   • Any player who dies is placed in SPECTATOR mode instead of respawning
 *     (mirroring vanilla hardcore behaviour in multiplayer).
 *   • When the event ends: difficulty is restored, all event-killed spectators
 *     are moved back to SURVIVAL with full health.
 *
 * State survives world re-entry and server restarts via player NBT:
 *   - votingmod_hardcore_expiry   – absolute expiry timestamp (ms)
 *   - votingmod_hardcore_duration – total duration (ms), for correct HUD bar fraction
 *   - votingmod_hardcore_died     – true if this player died during the event
 */
public class HardcoreModeHandler {

    public static final String TIMER_NAME = "Выше, сильнее, сложнее";

    private static final String NBT_EXPIRY_KEY   = "votingmod_hardcore_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_hardcore_duration";
    private static final String NBT_DIED_KEY     = "votingmod_hardcore_died";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-HardcoreRevert");
                t.setDaemon(true);
                return t;
            });

    /** Global event state. */
    public static volatile long       expiryMs        = 0;
    public static volatile long       durationMs      = 0;
    /** Original difficulty before the event, so we can restore it. null = server was restarted. */
    private static volatile Difficulty savedDifficulty = null;

    /** UUIDs of players who died during the current event and are now in spectator mode. */
    private static final Set<UUID> killedDuringEvent = ConcurrentHashMap.newKeySet();

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server, long duration) {
        durationMs = duration;
        expiryMs   = System.currentTimeMillis() + duration;

        // Save current difficulty and switch to HARD
        savedDifficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        // Persist to all online players
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CompoundTag tag = p.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        }

        // Start HUD timer
        EventTimerPacket timerStart = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);

        scheduleRevert(duration);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    /** When a player dies during the event, mark them and switch to spectator on respawn. */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        killedDuringEvent.add(player.getUUID());
        player.getPersistentData().putBoolean(NBT_DIED_KEY, true);
    }

    /** After the respawn completes, force spectator mode for hardcore-killed players. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.isEndConquered()) return; // entering the End — leave alone
        if (!killedDuringEvent.contains(player.getUUID())) return;
        player.setGameMode(GameType.SPECTATOR);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        CompoundTag tag = player.getPersistentData();

        if (!tag.contains(NBT_EXPIRY_KEY)) {
            // Not part of any active/recent event.
            // If the died-flag is somehow set, clean it up.
            if (tag.getBoolean(NBT_DIED_KEY)) tag.remove(NBT_DIED_KEY);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        long savedExpiry   = tag.getLong(NBT_EXPIRY_KEY);
        long savedDuration = tag.getLong(NBT_DURATION_KEY);
        long remaining     = savedExpiry - System.currentTimeMillis();

        if (remaining <= 0) {
            // Effect expired while offline — clean up everything.
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
            if (tag.getBoolean(NBT_DIED_KEY)) {
                tag.remove(NBT_DIED_KEY);
                // Player was in spectator when they went offline; restore them now.
                server.execute(() -> {
                    if (player.isSpectator()) {
                        player.setGameMode(GameType.SURVIVAL);
                        player.setHealth(player.getMaxHealth());
                    }
                });
            }
            expiryMs = 0;
            killedDuringEvent.remove(player.getUUID());
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        // Event still active — restore global state if lost (same-JVM re-enter / server restart).
        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            // Difficulty should already be HARD if we set it, but ensure it in any case.
            server.setDifficulty(Difficulty.HARD, true);
            scheduleRevert(remaining);
        }

        // If this player died during the event, enforce spectator immediately.
        if (tag.getBoolean(NBT_DIED_KEY)) {
            killedDuringEvent.add(player.getUUID());
            server.execute(() -> player.setGameMode(GameType.SPECTATOR));
        }

        // Re-send HUD timer with correct fraction.
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

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            expiryMs = 0;
            killedDuringEvent.clear();
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                // Restore original difficulty
                if (savedDifficulty != null) {
                    srv.setDifficulty(savedDifficulty, true);
                    savedDifficulty = null;
                }
                // Revive all event-killed players and clean up NBT
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    boolean wasDead = p.getPersistentData().getBoolean(NBT_DIED_KEY);
                    p.getPersistentData().remove(NBT_EXPIRY_KEY);
                    p.getPersistentData().remove(NBT_DURATION_KEY);
                    p.getPersistentData().remove(NBT_DIED_KEY);
                    if (wasDead && p.isSpectator()) {
                        p.setGameMode(GameType.SURVIVAL);
                        p.setHealth(p.getMaxHealth());
                    }
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
