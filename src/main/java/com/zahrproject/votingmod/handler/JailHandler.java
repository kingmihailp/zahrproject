package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the "Под шхонкой" event:
 *
 *   • A 5×5 iron bar cage is built around each online player (floor, ceiling, 4 walls).
 *   • 5 silverfish are spawned inside the cage and immediately target the player.
 *   • After 2 minutes: cage iron bars are removed, silverfish are discarded.
 *
 * Only AIR blocks are replaced, so natural stone/solid walls are not touched.
 * Cage blocks and silverfish UUIDs are tracked in memory; the HUD timer is
 * backed by NBT for correct display after reconnects.
 *
 * Limitation: if the server restarts during the event, cage iron bars remain in
 * the world permanently (they can be broken manually by any survival player or OP).
 */
public class JailHandler {

    public static final String TIMER_NAME = "Под шхонкой";

    private static final String NBT_EXPIRY_KEY   = "votingmod_jail_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_jail_duration";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-JailRevert");
                t.setDaemon(true);
                return t;
            });

    public static volatile long expiryMs   = 0;
    public static volatile long durationMs = 0;

    /** Cage data per player: dimension + placed-block positions + spawned silverfish UUIDs. */
    private record CageData(
            ResourceKey<Level> levelKey,
            List<BlockPos>     positions,
            List<UUID>         silverfishIds) {}

    private static final Map<UUID, CageData> activeCages = new ConcurrentHashMap<>();

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server, long duration) {
        durationMs = duration;
        expiryMs   = System.currentTimeMillis() + duration;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            buildCage(p);
            CompoundTag tag = p.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        }

        EventTimerPacket timerStart = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);

        scheduleRevert(duration);
    }

    // ── Cage construction / teardown ──────────────────────────────────────────

    /**
     * Builds a 5×5 iron bar cage around {@code player} and spawns 5 silverfish inside.
     * Only places bars where the block is currently air.
     */
    private static void buildCage(ServerPlayer player) {
        int px = Mth.floor(player.getX());
        int py = Mth.floor(player.getY());
        int pz = Mth.floor(player.getZ());
        ServerLevel level = player.serverLevel();

        List<BlockPos> placed = new ArrayList<>();

        // 5×5 footprint, 5 blocks tall (exterior from py-1 to py+3).
        // Perimeter = walls at x=px±2 or z=pz±2, floor at y=py-1, ceiling at y=py+3.
        // Interior (3×3×3 from py to py+2) is left free for the player and silverfish.
        for (int x = px - 2; x <= px + 2; x++) {
            for (int z = pz - 2; z <= pz + 2; z++) {
                for (int y = py - 1; y <= py + 3; y++) {
                    boolean isPerimeter = (x == px - 2 || x == px + 2
                            || z == pz - 2 || z == pz + 2
                            || y == py - 1 || y == py + 3);
                    if (!isPerimeter) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.IRON_BARS.defaultBlockState(), 3);
                        placed.add(pos);
                    }
                }
            }
        }

        // Spawn 5 silverfish in the interior (within ±1 block of the player on the xz plane).
        List<UUID> sfIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Silverfish sf = EntityType.SILVERFISH.create(level);
            if (sf == null) continue;
            double sx = px + (level.random.nextDouble() * 2.0 - 1.0);
            double sz = pz + (level.random.nextDouble() * 2.0 - 1.0);
            sf.moveTo(sx, py, sz, level.random.nextFloat() * 360f, 0f);
            sf.finalizeSpawn(level, level.getCurrentDifficultyAt(sf.blockPosition()),
                    MobSpawnType.MOB_SUMMONED, null, null);
            sf.setTarget(player);
            level.addFreshEntity(sf);
            sfIds.add(sf.getUUID());
        }

        activeCages.put(player.getUUID(), new CageData(level.dimension(), placed, sfIds));
    }

    /** Removes the cage iron bars and discards spawned silverfish for one player. */
    private static void removeCage(MinecraftServer srv, UUID playerUUID) {
        CageData cage = activeCages.remove(playerUUID);
        if (cage == null) return;

        ServerLevel level = srv.getLevel(cage.levelKey());
        if (level == null) return;

        for (BlockPos pos : cage.positions()) {
            if (level.getBlockState(pos).is(Blocks.IRON_BARS)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        for (UUID sfUUID : cage.silverfishIds()) {
            Entity e = level.getEntity(sfUUID);
            if (e instanceof Silverfish sf) sf.discard();
        }
    }

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            expiryMs = 0;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) { activeCages.clear(); return; }
            srv.execute(() -> {
                new HashSet<>(activeCages.keySet()).forEach(uuid -> removeCage(srv, uuid));
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    p.getPersistentData().remove(NBT_EXPIRY_KEY);
                    p.getPersistentData().remove(NBT_DURATION_KEY);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
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
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
            expiryMs = 0;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        // Restore global timer state if lost (server restart).
        // NOTE: cage blocks are still in the world after restart but we've lost memory of
        // which blocks we placed, so we rebuild the cage with fresh blocks + silverfish.
        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            if (!activeCages.containsKey(player.getUUID())) {
                server.execute(() -> buildCage(player));
            }
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
}
