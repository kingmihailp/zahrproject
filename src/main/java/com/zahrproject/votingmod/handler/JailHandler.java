package com.zahrproject.votingmod.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.block.Blocks;

/**
 * Manages the "Под шхонкой" event:
 *
 *   A 5×5 iron bar cage is built around each online player and 5 silverfish
 *   are spawned inside, immediately targeting that player.
 *
 *   There is no timer and no automatic cleanup — the cage and silverfish
 *   persist until broken/killed normally.
 *   Only AIR blocks are replaced, so existing solid walls are not touched.
 */
public class JailHandler {

    public static void activate(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            buildCage(player);
        }
    }

    private static void buildCage(ServerPlayer player) {
        int px = Mth.floor(player.getX());
        int py = Mth.floor(player.getY());
        int pz = Mth.floor(player.getZ());
        ServerLevel level = player.serverLevel();

        // 5×5 perimeter (walls at x=px±2 or z=pz±2) + floor at y=py-1 + ceiling at y=py+3.
        // Interior 3×3×3 (py to py+2) is left free for the player and silverfish.
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
                    }
                }
            }
        }

        // Spawn 5 silverfish inside the cage (within ±1 block of the player on xz).
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
        }
    }
}
