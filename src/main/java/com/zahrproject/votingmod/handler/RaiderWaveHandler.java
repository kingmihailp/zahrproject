package com.zahrproject.votingmod.handler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages the "Они следят, они ищут" event — a one-shot raid wave:
 *
 *   One random wave composition is chosen from five escalating presets:
 *     Wave 1 – 5 Pillagers
 *     Wave 2 – 3 Pillagers + 3 Vindicators
 *     Wave 3 – 3 Pillagers + 2 Vindicators + 1 Witch
 *     Wave 4 – 2 Pillagers + 3 Vindicators + 1 Ravager
 *     Wave 5 – 1 Pillager  + 3 Vindicators + 2 Witches + 1 Evoker + 1 Ravager
 *
 *   Mobs from that composition are spawned around every online player (8–14 blocks away,
 *   at the player's Y level) and immediately set to target that player.
 *
 *   This is a one-shot event: spawned mobs behave as regular hostile entities
 *   and are not cleaned up automatically.
 */
public class RaiderWaveHandler {

    /** One entry in a wave composition: entity type + count. */
    private record WaveEntry(EntityType<? extends Mob> type, int count) {}

    private static final List<List<WaveEntry>> WAVES = buildWaves();

    private static List<List<WaveEntry>> buildWaves() {
        List<List<WaveEntry>> waves = new ArrayList<>();

        // Wave 1
        waves.add(List.of(
                new WaveEntry(EntityType.PILLAGER, 5)
        ));
        // Wave 2
        waves.add(List.of(
                new WaveEntry(EntityType.PILLAGER,   3),
                new WaveEntry(EntityType.VINDICATOR, 3)
        ));
        // Wave 3
        waves.add(List.of(
                new WaveEntry(EntityType.PILLAGER,   3),
                new WaveEntry(EntityType.VINDICATOR, 2),
                new WaveEntry(EntityType.WITCH,      1)
        ));
        // Wave 4
        waves.add(List.of(
                new WaveEntry(EntityType.PILLAGER,   2),
                new WaveEntry(EntityType.VINDICATOR, 3),
                new WaveEntry(EntityType.RAVAGER,    1)
        ));
        // Wave 5
        waves.add(List.of(
                new WaveEntry(EntityType.PILLAGER,   1),
                new WaveEntry(EntityType.VINDICATOR, 3),
                new WaveEntry(EntityType.WITCH,      2),
                new WaveEntry(EntityType.EVOKER,     1),
                new WaveEntry(EntityType.RAVAGER,    1)
        ));

        return waves;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Picks a random wave and spawns it near every online player.
     *
     * @return the index (1-based) of the chosen wave, for use in the broadcast message.
     */
    public static int activate(MinecraftServer server) {
        Random rng = new Random();
        int waveIndex = rng.nextInt(WAVES.size());
        List<WaveEntry> wave = WAVES.get(waveIndex);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            spawnWaveForPlayer(player, wave, rng);
        }

        return waveIndex + 1; // 1-based for messages
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void spawnWaveForPlayer(ServerPlayer player, List<WaveEntry> wave, Random rng) {
        ServerLevel level = player.serverLevel();

        for (WaveEntry entry : wave) {
            for (int i = 0; i < entry.count(); i++) {
                spawnOne(entry.type(), level, player, rng);
            }
        }
    }

    /**
     * Spawns a single raider of {@code type} 8–14 blocks horizontally from the player
     * at the player's Y coordinate.  The mob is immediately set to target the player.
     */
    private static void spawnOne(EntityType<? extends Mob> type,
                                  ServerLevel level,
                                  ServerPlayer player,
                                  Random rng) {
        double angle  = rng.nextDouble() * 2 * Math.PI;
        double radius = 8.0 + rng.nextDouble() * 6.0; // 8–14 blocks
        double x = player.getX() + Math.cos(angle) * radius;
        double z = player.getZ() + Math.sin(angle) * radius;
        double y = player.getY();

        Mob mob = type.create(level);
        if (mob == null) return;

        mob.moveTo(x, y, z, rng.nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
        mob.setTarget(player);
        level.addFreshEntity(mob);
    }
}
