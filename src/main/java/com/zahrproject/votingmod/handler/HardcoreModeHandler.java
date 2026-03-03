package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Выше, сильнее, сложнее" event:
 *
 *   • World difficulty is forced to HARD for the duration.
 *   • Every hostile mob that joins the world triggers 3 extra spawns of the
 *     same type at roughly the same location (x4 total spawn rate).
 *   • After 5 minutes everything reverts automatically.
 *
 * Extra entities are tracked by UUID to prevent recursive multiplication.
 * Pending spawns are processed on the main server thread via ServerTickEvent
 * to avoid concurrent-modification issues.
 */
public class HardcoreModeHandler {

    public static final String TIMER_NAME = "Выше, сильнее, сложнее";

    private static final String NBT_EXPIRY_KEY   = "votingmod_hardcore_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_hardcore_duration";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-HarderRevert");
                t.setDaemon(true);
                return t;
            });

    public static volatile long expiryMs   = 0;
    public static volatile long durationMs = 0;
    private static volatile Difficulty savedDifficulty = null;

    /** UUIDs of extra-spawned entities — skip in onEntityJoin to prevent recursion. */
    private static final Set<UUID> extraSpawnIds = ConcurrentHashMap.newKeySet();

    private record SpawnEntry(EntityType<?> type, BlockPos pos, ServerLevel level) {}
    private static final Queue<SpawnEntry> pendingSpawns = new ConcurrentLinkedQueue<>();

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server, long duration) {
        durationMs = duration;
        expiryMs   = System.currentTimeMillis() + duration;

        savedDifficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CompoundTag tag = p.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        }

        EventTimerPacket timerStart = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);

        scheduleRevert(duration);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    /**
     * When any hostile mob joins a server world, queue 3 extra copies nearby.
     * Extras are identified via UUID so they don't trigger further multiplication.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!isActive()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (extraSpawnIds.remove(monster.getUUID())) return; // our extra — skip

        EntityType<?> type = monster.getType();
        BlockPos pos = monster.blockPosition();
        for (int i = 0; i < 3; i++) {
            pendingSpawns.add(new SpawnEntry(type, pos, level));
        }
    }

    /** Flush the pending-spawn queue each server tick (main thread, safe to modify world). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingSpawns.isEmpty()) return;
        if (!isActive()) {
            pendingSpawns.clear();
            return;
        }
        // Process up to 30 extra spawns per tick to avoid lag spikes.
        int budget = 30;
        SpawnEntry entry;
        while (budget-- > 0 && (entry = pendingSpawns.poll()) != null) {
            ServerLevel lvl = entry.level();
            if (!lvl.isLoaded(entry.pos())) continue;
            Entity raw = entry.type().create(lvl);
            if (!(raw instanceof Monster mob)) continue;
            int dx = lvl.random.nextInt(5) - 2;
            int dz = lvl.random.nextInt(5) - 2;
            BlockPos target = entry.pos().offset(dx, 0, dz);
            mob.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                    lvl.random.nextFloat() * 360f, 0f);
            extraSpawnIds.add(mob.getUUID());
            lvl.addFreshEntity(mob);
        }
    }

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

        // Restore global state after server restart
        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            server.setDifficulty(Difficulty.HARD, true);
            scheduleRevert(remaining);
        }

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
            pendingSpawns.clear();
            extraSpawnIds.clear();
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                if (savedDifficulty != null) {
                    srv.setDifficulty(savedDifficulty, true);
                    savedDifficulty = null;
                }
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
