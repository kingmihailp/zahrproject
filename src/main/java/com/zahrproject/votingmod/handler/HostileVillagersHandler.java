package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Пассивная агрессия" event:
 *  - Every second, nearby villagers (within 20 blocks) pursue each player.
 *  - Every 2 seconds, villagers within reach deal 1 heart of damage.
 *  - HUD timer shown to all clients; state survives reconnects via NBT.
 */
public class HostileVillagersHandler {

    public static final String TIMER_NAME = "Пассивная агрессия";

    private static final String NBT_EXPIRY_KEY   = "votingmod_passive_aggression_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_passive_aggression_duration";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-VillagerRevert");
                t.setDaemon(true);
                return t;
            });

    /** Absolute expiry timestamp (ms). 0 = not active. */
    public static volatile long expiryMs   = 0;
    /** Full duration of the current event (ms). */
    public static volatile long durationMs = 0;

    private static int tickCounter = 0;

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server, long duration) {
        durationMs = duration;
        expiryMs   = System.currentTimeMillis() + duration;

        // Persist expiry to all online players so reconnecting clients can restore it
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CompoundTag tag = p.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        }

        // Start HUD timer on all clients
        EventTimerPacket timerStart = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);

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
        }, duration, TimeUnit.MILLISECONDS);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isActive()) return;
        tickCounter++;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Every 20 ticks (1 s): make nearby villagers pursue players
        if (tickCounter % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = player.serverLevel();
                AABB area = player.getBoundingBox().inflate(20);
                for (Villager v : level.getEntitiesOfClass(Villager.class, area)) {
                    v.getNavigation().moveTo(player, 1.0);
                    v.getLookControl().setLookAt(player, 30, 30);
                }
            }
        }

        // Every 40 ticks (2 s): deal 1 heart damage if a villager is within reach
        if (tickCounter % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isCreative() || player.isSpectator()) continue;
                ServerLevel level = player.serverLevel();
                AABB area = player.getBoundingBox().inflate(2.0);
                List<Villager> inRange = level.getEntitiesOfClass(Villager.class, area);
                if (!inRange.isEmpty()) {
                    player.hurt(level.damageSources().mobAttack(inRange.get(0)), 2.0f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(NBT_EXPIRY_KEY)) {
            // No saved state — clear any stale HUD bar the client might have
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        long savedExpiry   = tag.getLong(NBT_EXPIRY_KEY);
        long savedDuration = tag.getLong(NBT_DURATION_KEY);
        long remaining     = savedExpiry - System.currentTimeMillis();

        if (remaining <= 0) {
            // Expired while offline
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
            expiryMs = 0;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        // Still active — restore global state if it was lost (server restart)
        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
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
            }, remaining, TimeUnit.MILLISECONDS);
        }

        // Re-send HUD timer with correct fraction
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
}
