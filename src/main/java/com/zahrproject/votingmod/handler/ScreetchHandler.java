package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.entity.ModEntities;
import com.zahrproject.votingmod.entity.ScreetchEntity;
import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Обрабатывает логику спавна скритчей для события «они такая мелочь».
 *
 * Цикл на каждого активного игрока:
 *   1. Если текущий скритч всё ещё жив — пропускаем.
 *   2. Уменьшаем кулдаун. Если кулдаун > 0 — пропускаем.
 *   3. Если уровень блочного освещения > 7 — пропускаем.
 *   4. С вероятностью 40% спавним скритча в 3 блоках за спиной игрока
 *      и устанавливаем кулдаун на 30 секунд.
 *   5. Если вероятность не сработала — маленький кулдаун 15 секунд
 *      перед следующей проверкой.
 *
 * Состояние сохраняется между переподключениями через NBT игрока.
 */
public class ScreetchHandler {

    public static final String TIMER_NAME = "они такая мелочь";

    private static final String NBT_EXPIRY_KEY   = "votingmod_screetch_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_screetch_duration";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-ScreetchEnd");
                t.setDaemon(true);
                return t;
            });

    public static volatile long expiryMs   = 0;
    public static volatile long durationMs = 0;

    /** UUID игроков, для которых событие сейчас активно. */
    public static final Set<UUID> activePlayers = new HashSet<>();

    /** Кулдаун в тиках до следующей попытки спавна для каждого игрока. */
    private static final Map<UUID, Integer> spawnCooldown = new HashMap<>();

    /** UUID текущего живого скритча для каждого игрока. */
    private static final Map<UUID, UUID> activeScreetch = new HashMap<>();

    // ── API ───────────────────────────────────────────────────────────────────

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    public static void activate(MinecraftServer server, long duration) {
        durationMs = duration;
        expiryMs   = System.currentTimeMillis() + duration;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            activePlayers.add(uuid);
            spawnCooldown.put(uuid, 200); // 10 секунд до первого скритча

            CompoundTag tag = player.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        }

        EventTimerPacket timerPkt = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerPkt);

        scheduleRevert(duration);
    }

    public static void deactivate() {
        expiryMs = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Kill all live screetches
            for (Map.Entry<UUID, UUID> entry : activeScreetch.entrySet()) {
                UUID playerUUID  = entry.getKey();
                UUID screetchUUID = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    Entity e = player.serverLevel().getEntity(screetchUUID);
                    if (e != null) e.discard();
                }
            }
            // Clear NBT and send timer-end packet
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                CompoundTag tag = p.getPersistentData();
                tag.remove(NBT_EXPIRY_KEY);
                tag.remove(NBT_DURATION_KEY);
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                        new EventTimerPacket(TIMER_NAME, -1, durationMs));
            }
        }

        activePlayers.clear();
        spawnCooldown.clear();
        activeScreetch.clear();
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(NBT_EXPIRY_KEY)) {
            // No saved state — send a cleared timer so the HUD doesn't show stale data
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        long savedExpiry   = tag.getLong(NBT_EXPIRY_KEY);
        long savedDuration = tag.getLong(NBT_DURATION_KEY);
        long remaining     = savedExpiry - System.currentTimeMillis();

        if (remaining <= 0) {
            // Expired while offline — clean up
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        // Event still active — restore global state if needed and re-add player
        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            scheduleRevert(remaining);
        }

        UUID uuid = player.getUUID();
        activePlayers.add(uuid);
        spawnCooldown.put(uuid, 200); // brief grace period before first spawn attempt

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EventTimerPacket(TIMER_NAME, remaining, savedDuration));
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

    // ── Tick ──────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (activePlayers.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Remove players who are offline (they'll be re-added on login via NBT)
        activePlayers.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);

        for (UUID uuid : activePlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            ServerLevel level = player.serverLevel();

            // ── 1. Есть живой скритч? ─────────────────────────────────────────
            UUID curUUID = activeScreetch.get(uuid);
            if (curUUID != null) {
                Entity e = level.getEntity(curUUID);
                if (e != null && !e.isRemoved()) continue; // скритч ещё жив
                activeScreetch.remove(uuid);
            }

            // ── 2. Кулдаун ───────────────────────────────────────────────────
            int cd = spawnCooldown.getOrDefault(uuid, 0);
            if (cd > 0) {
                spawnCooldown.put(uuid, cd - 1);
                continue;
            }

            // ── 3. Уровень освещения ─────────────────────────────────────────
            BlockPos pos = player.blockPosition();
            if (level.getBrightness(LightLayer.BLOCK, pos) > 7) {
                spawnCooldown.put(uuid, 40); // проверяем снова через 2 секунды
                continue;
            }

            // ── 4. Вероятность спавна ─────────────────────────────────────────
            if (level.random.nextFloat() > 0.40f) {
                spawnCooldown.put(uuid, 300); // 15 секунд до следующей попытки
                continue;
            }

            // ── 5. Спавн позади игрока ────────────────────────────────────────
            Vec3 look     = player.getLookAngle();
            double spawnX = player.getX() - look.x * 3.0;
            double spawnY = player.getY();
            double spawnZ = player.getZ() - look.z * 3.0;

            BlockPos spawnBlock = new BlockPos((int) spawnX, (int) spawnY, (int) spawnZ);
            if (!level.getBlockState(spawnBlock).isAir() &&
                    !level.getBlockState(spawnBlock.above()).isAir()) {
                spawnY += 1.0;
            }

            ScreetchEntity screetch = new ScreetchEntity(ModEntities.SCREETCH.get(), level);
            screetch.setPos(spawnX, spawnY, spawnZ);
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            screetch.setYRot(yaw);
            screetch.setTargetPlayer(uuid);

            level.addFreshEntity(screetch);
            activeScreetch.put(uuid, screetch.getUUID());

            player.playNotifySound(SoundEvents.CREEPER_PRIMED,
                    SoundSource.HOSTILE, 0.45f, 1.9f);

            spawnCooldown.put(uuid, 600); // 30 секунд до следующего спавна
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) {
                expiryMs = 0;
                return;
            }
            srv.execute(ScreetchHandler::deactivate);
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
