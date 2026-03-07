package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.entity.ModEntities;
import com.zahrproject.votingmod.entity.ScreetchEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

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
 */
public class ScreetchHandler {

    /** UUID игроков, для которых событие сейчас активно. */
    public static final Set<UUID> activePlayers = new HashSet<>();

    /** Кулдаун в тиках до следующей попытки спавна для каждого игрока. */
    private static final Map<UUID, Integer> spawnCooldown = new HashMap<>();

    /** UUID текущего живого скритча для каждого игрока. */
    private static final Map<UUID, UUID> activeScreetch = new HashMap<>();

    // ── API ───────────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server) {
        // Небольшой начальный кулдаун — сначала «ничего не происходит»
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            activePlayers.add(player.getUUID());
            spawnCooldown.put(player.getUUID(), 200); // 10 секунд до первого скритча
        }
    }

    public static void deactivate() {
        // Убираем всех живых скритчей
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (Map.Entry<UUID, UUID> entry : activeScreetch.entrySet()) {
                UUID playerUUID = entry.getKey();
                UUID screetchUUID = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    Entity e = player.serverLevel().getEntity(screetchUUID);
                    if (e != null) e.discard();
                }
            }
        }
        activePlayers.clear();
        spawnCooldown.clear();
        activeScreetch.clear();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (activePlayers.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Убираем игроков которые вышли
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
            int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
            int skyLight   = level.getBrightness(LightLayer.SKY, pos);
            // Учитываем дневное небесное освещение
            int effectiveLight = Math.max(blockLight, skyLight - level.getSkyDarken());
            if (effectiveLight > 7) {
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
            // Позиция в 3 блоках за спиной (противоположно взгляду)
            double spawnX = player.getX() - look.x * 3.0;
            double spawnY = player.getY();
            double spawnZ = player.getZ() - look.z * 3.0;

            // Убедимся, что Y не уходит в блок
            BlockPos spawnBlock = new BlockPos((int) spawnX, (int) spawnY, (int) spawnZ);
            if (!level.getBlockState(spawnBlock).isAir() &&
                    !level.getBlockState(spawnBlock.above()).isAir()) {
                // Место занято — пробуем немного выше
                spawnY += 1.0;
            }

            ScreetchEntity screetch = new ScreetchEntity(ModEntities.SCREETCH.get(), level);
            screetch.setPos(spawnX, spawnY, spawnZ);
            // Поворачиваем скритча лицом к игроку
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            screetch.setYRot(yaw);
            screetch.setTargetPlayer(uuid);

            level.addFreshEntity(screetch);
            activeScreetch.put(uuid, screetch.getUUID());

            // 30 секунд до следующего спавна (после гибели/откуса этого)
            spawnCooldown.put(uuid, 600);
        }
    }
}
