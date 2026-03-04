package com.zahrproject.votingmod.handler;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the "Лор стойки для брони…" event.
 *
 * Spawns one ArmorStand near each player equipped with an iron axe (main hand)
 * and iron chestplate.  The stand chases its target player each tick and deals
 * melee damage when close.  Blue→red dust_transition particles orbit the stand
 * until it is destroyed.
 *
 * Only stands tagged with {@value #NBT_TAG} are managed; all other ArmorStands
 * in the world are left completely untouched.
 */
public class ArmorStandLorHandler {

    private static final String NBT_TAG = "votingmod_lor_stand";

    /** Blue (from) → Red (to), scale 1.0. */
    private static final DustColorTransitionOptions DUST =
            new DustColorTransitionOptions(new Vector3f(0f, 0f, 1f), new Vector3f(1f, 0f, 0f), 1.0f);

    private static final double CHASE_SPEED  = 0.15;
    private static final double ATTACK_RANGE = 2.5;
    private static final float  ATTACK_DAMAGE = 4.0f; // 2 hearts

    private static final Random RANDOM = new Random();

    /** standUUID → playerUUID of initial target. */
    private static final ConcurrentHashMap<UUID, UUID> trackedStands = new ConcurrentHashMap<>();

    private static int tickCounter = 0;

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            UUID standId = spawnStand(player);
            if (standId != null) trackedStands.put(standId, player.getUUID());
        }
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (trackedStands.isEmpty()) return;

        tickCounter++;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, UUID> entry : trackedStands.entrySet()) {
            UUID standId  = entry.getKey();
            UUID playerId = entry.getValue();

            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target == null || target.isSpectator()) {
                toRemove.add(standId);
                continue;
            }

            ServerLevel level = target.serverLevel();
            if (!(level.getEntity(standId) instanceof ArmorStand stand)) {
                toRemove.add(standId);
                continue;
            }
            if (!stand.isAlive()) {
                toRemove.add(standId);
                continue;
            }

            double dx = target.getX() - stand.getX();
            double dy = target.getY() - stand.getY();
            double dz = target.getZ() - stand.getZ();
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            double fullDist  = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // ── Chase ──────────────────────────────────────────────────────
            if (horizDist > 0.8) {
                double vx = (dx / horizDist) * CHASE_SPEED;
                double vz = (dz / horizDist) * CHASE_SPEED;
                stand.setDeltaMovement(vx, stand.getDeltaMovement().y, vz);
                stand.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
            }

            // ── Attack every 20 ticks when in range ────────────────────────
            if (tickCounter % 20 == 0 && fullDist < ATTACK_RANGE && !target.isCreative()) {
                target.hurt(level.damageSources().mobAttack(stand), ATTACK_DAMAGE);
            }

            // ── Particles (every tick) ─────────────────────────────────────
            level.sendParticles(DUST,
                    stand.getX(), stand.getY() + 1.0, stand.getZ(),
                    3, 0.4, 0.5, 0.4, 0.0);
        }

        toRemove.forEach(trackedStands::remove);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static UUID spawnStand(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) return null;

        double ox = (RANDOM.nextDouble() - 0.5) * 4;
        double oz = (RANDOM.nextDouble() - 0.5) * 4;
        stand.moveTo(player.getX() + ox, player.getY(), player.getZ() + oz, 0f, 0f);

        stand.setItemSlot(EquipmentSlot.MAINHAND,  new ItemStack(Items.IRON_AXE));
        stand.setItemSlot(EquipmentSlot.CHEST,     new ItemStack(Items.IRON_CHESTPLATE));

        stand.getPersistentData().putBoolean(NBT_TAG, true);

        level.addFreshEntity(stand);
        return stand.getUUID();
    }
}
