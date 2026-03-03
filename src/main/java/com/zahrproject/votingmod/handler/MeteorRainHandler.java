package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the "Метеоритный дождь" event.
 *
 * For every online player, 6–10 meteors are launched in sequence.
 * Each meteor is a {@link FallingBlockEntity} (magma block, or deepslate
 * diamond ore with ~7% chance) given a diagonal initial velocity.
 * A smoke/flame particle trail follows the entity each tick.
 *
 * On player hit  → 3–5 hearts of magic damage + small explosion.
 * On ground hit  → small explosion; diamond meteors leave a deepslate
 *                  diamond ore block at the impact site.
 */
public class MeteorRainHandler {

    public static final String TIMER_NAME = "Метеоритный дождь";

    /** Total HUD duration shown to players (ms). */
    private static final long EVENT_DURATION_MS = 20_000L;

    private static final Random RANDOM = new Random();

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-MeteorRain");
                t.setDaemon(true);
                return t;
            });

    /** Active meteors: entity UUID → metadata used in the tick handler. */
    private static final Map<UUID, MeteorData> activeMeteors = new ConcurrentHashMap<>();

    // ── Internal data ─────────────────────────────────────────────────────────

    private static class MeteorData {
        final FallingBlockEntity entity;
        final ResourceKey<Level> dimension;
        final boolean isDiamond;
        /** Last observed position — used as fallback for explosion placement. */
        Vec3 lastPos;
        boolean handled = false;

        MeteorData(FallingBlockEntity entity, boolean isDiamond) {
            this.entity    = entity;
            this.dimension = entity.level().dimension();
            this.isDiamond = isDiamond;
            this.lastPos   = entity.position();
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server) {
        // HUD timer — start
        EventTimerPacket start = new EventTimerPacket(TIMER_NAME, EVENT_DURATION_MS, EVENT_DURATION_MS);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), start);

        // HUD timer — clear after event ends
        SCHEDULER.schedule(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                EventTimerPacket end = new EventTimerPacket(TIMER_NAME, 0, 0);
                for (ServerPlayer p : srv.getPlayerList().getPlayers())
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), end);
            });
        }, EVENT_DURATION_MS, TimeUnit.MILLISECONDS);

        // Schedule meteors per player
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int count = 6 + RANDOM.nextInt(5); // 6–10 meteors
            for (int i = 0; i < count; i++) {
                long delay = i * (900L + RANDOM.nextInt(600)); // 0.9–1.5 s gaps
                SCHEDULER.schedule(() -> {
                    MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
                    if (srv == null) return;
                    srv.execute(() -> {
                        if (player.isAlive()) spawnMeteor(player);
                    });
                }, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    // ── Meteor spawn ──────────────────────────────────────────────────────────

    private static void spawnMeteor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        // Spawn position: 45–60 blocks above player, random horizontal offset
        double ox = (RANDOM.nextDouble() - 0.5) * 20; // ±10 blocks
        double oz = (RANDOM.nextDouble() - 0.5) * 20;
        double spawnX = player.getX() + ox;
        double spawnY = player.getY() + 45 + RANDOM.nextInt(15);
        double spawnZ = player.getZ() + oz;

        // 7% chance for a diamond meteor
        boolean isDiamond = RANDOM.nextFloat() < 0.07f;
        BlockState state = isDiamond
                ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()
                : Blocks.MAGMA_BLOCK.defaultBlockState();

        BlockPos spawnPos = BlockPos.containing(spawnX, spawnY, spawnZ);
        FallingBlockEntity meteor = FallingBlockEntity.fall(level, spawnPos, state);

        // Prevent the block from being dropped as an item if it can't be placed.
        // FallingBlockEntity will still place the block on landing; we remove it
        // ourselves in the tick handler before triggering the explosion.
        meteor.dropItem = false;

        // Diagonal velocity aimed near the player (with slight spread)
        double targX = player.getX() + (RANDOM.nextDouble() - 0.5) * 6;
        double targZ = player.getZ() + (RANDOM.nextDouble() - 0.5) * 6;
        double dx = targX - spawnX;
        double dz = targZ - spawnZ;
        double horizLen = Math.sqrt(dx * dx + dz * dz);
        double speed = 0.55 + RANDOM.nextDouble() * 0.35; // 0.55–0.9 blocks/tick
        double vx = horizLen > 0.01 ? (dx / horizLen) * speed : 0;
        double vz = horizLen > 0.01 ? (dz / horizLen) * speed : 0;
        meteor.setDeltaMovement(vx, -0.25, vz);

        activeMeteors.put(meteor.getUUID(), new MeteorData(meteor, isDiamond));
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (activeMeteors.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, MeteorData> entry : activeMeteors.entrySet()) {
            MeteorData data = entry.getValue();

            if (data.handled) {
                toRemove.add(entry.getKey());
                continue;
            }

            FallingBlockEntity entity = data.entity;

            // ── Ground landing (entity discarded by vanilla falling-block tick) ──
            if (!entity.isAlive()) {
                data.handled = true;
                toRemove.add(entry.getKey());

                ServerLevel level = server.getLevel(data.dimension);
                if (level == null) continue;

                // entity fields (position, blockPosition) remain valid after discard
                Vec3 pos = entity.position();
                BlockPos bp = entity.blockPosition();

                // FallingBlockEntity may have placed magma/ore on landing — remove it
                // so the explosion isn't blocked by our own block.
                BlockState placed = level.getBlockState(bp);
                if (placed.is(Blocks.MAGMA_BLOCK) || placed.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                    level.removeBlock(bp, false);
                }

                // Explosion first, then place diamond ore into the resulting crater.
                level.explode(null, pos.x, pos.y, pos.z, 2.0f, Level.ExplosionInteraction.BLOCK);

                if (data.isDiamond) {
                    // Place reward at the impact point if the explosion left room.
                    if (level.getBlockState(bp).canBeReplaced()) {
                        level.setBlock(bp, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 3);
                    } else {
                        // Try one block below (bottom of the small crater).
                        BlockPos below = bp.below();
                        if (level.getBlockState(below).canBeReplaced()) {
                            level.setBlock(below, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 3);
                        }
                    }
                }
                continue;
            }

            // Update last-known position while the meteor is still in the air
            data.lastPos = entity.position();

            ServerLevel level = server.getLevel(data.dimension);
            if (level == null) continue;

            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();

            // ── Direct player hit ──────────────────────────────────────────────
            AABB hitbox = entity.getBoundingBox().inflate(0.4);
            List<ServerPlayer> hit = level.getEntitiesOfClass(ServerPlayer.class, hitbox);
            if (!hit.isEmpty()) {
                data.handled = true;
                toRemove.add(entry.getKey());
                for (ServerPlayer p : hit) {
                    // 3–5 hearts = 6–10 HP
                    float dmg = 6.0f + level.random.nextFloat() * 4.0f;
                    p.hurt(level.damageSources().magic(), dmg);
                }
                level.explode(null, x, y, z, 2.0f, Level.ExplosionInteraction.BLOCK);
                entity.kill();
                continue;
            }

            // ── Particle trail (campfire smoke + smoke + flame) ───────────────
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y, z, 2, 0.15, 0.15, 0.15, 0.005);
            level.sendParticles(ParticleTypes.SMOKE,
                    x, y, z, 3, 0.10, 0.10, 0.10, 0.04);
            level.sendParticles(ParticleTypes.FLAME,
                    x, y, z, 2, 0.10, 0.10, 0.10, 0.06);
        }

        toRemove.forEach(activeMeteors::remove);
    }
}
