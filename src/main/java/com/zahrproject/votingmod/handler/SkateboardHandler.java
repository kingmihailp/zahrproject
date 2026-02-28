package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side handler for the Skateboard enchantment.
 *
 * Movement:
 *   - Holding sprint (Ctrl) while shield with Skateboard is in offhand
 *     builds up speed; releasing Ctrl decelerates.
 *   - The board travels in a "skate direction" that is separate from the
 *     player's look direction, so it persists through ricochets.
 *   - When the player is NOT in ricochet mode, the skate direction
 *     smoothly steers toward the look direction each tick (natural turning).
 *
 * Ricochet:
 *   - Every tick we compare the player's actual position change (reported
 *     by the client) with the velocity we sent last tick.
 *   - If either the X or Z component is blocked (< 50% of expected), we
 *     reflect the corresponding component of the skate direction.
 *   - A ricochet locks the skate direction for RIC_TICKS ticks so the
 *     player slides away from the wall rather than slamming back into it.
 *
 * Collision damage:
 *   - At sufficient speed, nearby mobs/players take 2 hearts (4 HP) each,
 *     subject to a per-entity 20-tick cooldown.
 */
public class SkateboardHandler {

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final double ACCELERATION      = 0.015;
    private static final double DECELERATION      = 0.008;
    private static final double MAX_SPEED         = 0.6;
    private static final double HIT_SPEED_MIN     = 0.15;
    private static final float  HIT_DAMAGE        = 4.0f;   // 2 hearts
    private static final long   HIT_COOLDOWN      = 20L;    // ticks
    /** Maximum turn rate toward the look direction while NOT in ricochet. */
    private static final double MAX_STEER_DEG     = 4.0;
    /** Ticks to hold the reflected direction after a wall bounce. */
    private static final int    RIC_TICKS         = 10;
    /** Collision detection threshold: actual < expected × this → wall hit. */
    private static final double RIC_THRESHOLD     = 0.5;

    // ── Per-player state ──────────────────────────────────────────────────────
    private static final Map<UUID, Double>   skateSpeed    = new HashMap<>();
    /** Normalised movement direction [dirX, dirZ]. */
    private static final Map<UUID, double[]> skateDir      = new HashMap<>();
    /** Ticks remaining in the current ricochet lock. */
    private static final Map<UUID, Integer>  ricTimer      = new HashMap<>();
    /** Position stored at the END of the previous tick. */
    private static final Map<UUID, Vec3>     prevPos       = new HashMap<>();
    /** Velocity [dx, dz] we sent last tick. */
    private static final Map<UUID, double[]> prevExpected  = new HashMap<>();
    /** Hit-damage cooldown per target entity. */
    private static final Map<UUID, Long>     hitCooldown   = new HashMap<>();

    // ── Main tick ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side  != LogicalSide.SERVER)  return;
        if (!(event.player instanceof ServerPlayer player)) return;

        ItemStack offhand = player.getOffhandItem();
        boolean hasSkateShield = offhand.getItem() == Items.SHIELD
                && EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.SKATEBOARD.get(), offhand) > 0;

        UUID id = player.getUUID();

        if (!hasSkateShield) {
            cleanup(id);
            return;
        }

        // ── Speed ─────────────────────────────────────────────────────────────
        boolean accelerating = player.isSprinting();
        double speed = skateSpeed.getOrDefault(id, 0.0);
        speed = accelerating
                ? Math.min(speed + ACCELERATION, MAX_SPEED)
                : Math.max(speed - DECELERATION, 0.0);

        if (speed < 0.001) {
            cleanup(id);
            return;
        }
        skateSpeed.put(id, speed);

        // ── Ricochet detection ────────────────────────────────────────────────
        // Compare the player's actual position change with what we expected.
        // (The server receives client-reported positions, so this reflects
        //  the real movement after the client's collision detection ran.)
        Vec3 curPos = player.position();
        boolean justRicocheted = false;

        double[] dir;
        int rt = ricTimer.getOrDefault(id, 0);

        if (prevPos.containsKey(id) && prevExpected.containsKey(id)) {
            Vec3    prev   = prevPos.get(id);
            double[] exp   = prevExpected.get(id);
            double  movedX = curPos.x - prev.x;
            double  movedZ = curPos.z - prev.z;

            boolean xHit = Math.abs(exp[0]) > 0.02
                    && Math.abs(movedX) < Math.abs(exp[0]) * RIC_THRESHOLD;
            boolean zHit = Math.abs(exp[1]) > 0.02
                    && Math.abs(movedZ) < Math.abs(exp[1]) * RIC_THRESHOLD;

            if (xHit || zHit) {
                double[] old = skateDir.getOrDefault(id, dirFromYaw(player.getYRot()));
                double newDirX = xHit ? -old[0] : old[0];
                double newDirZ = zHit ? -old[1] : old[1];
                skateDir.put(id, new double[]{ newDirX, newDirZ });
                ricTimer.put(id, RIC_TICKS);
                rt = RIC_TICKS;
                justRicocheted = true;
            }
        }

        // ── Skate direction ───────────────────────────────────────────────────
        if (justRicocheted) {
            dir = skateDir.get(id);
        } else if (rt > 0) {
            // Still locked to ricochet direction
            dir = skateDir.getOrDefault(id, dirFromYaw(player.getYRot()));
            ricTimer.put(id, rt - 1);
        } else {
            // Normal steering: smoothly turn the skate direction toward look
            double[] current = skateDir.getOrDefault(id, dirFromYaw(player.getYRot()));
            double lookYaw = Math.toRadians(player.getYRot());
            double[] look  = new double[]{ -Math.sin(lookYaw), Math.cos(lookYaw) };

            // Angle between current dir and look dir
            double cross = current[0] * look[1] - current[1] * look[0]; // sin(angle)
            double dot   = current[0] * look[0] + current[1] * look[1]; // cos(angle)
            double angleDiff = Math.atan2(cross, dot); // [-π, π]

            double maxTurn = Math.toRadians(MAX_STEER_DEG);
            double turn    = Math.signum(angleDiff) * Math.min(Math.abs(angleDiff), maxTurn);

            // Rotate current dir by 'turn' radians
            double cos = Math.cos(turn), sin = Math.sin(turn);
            double newX = current[0] * cos - current[1] * sin;
            double newZ = current[0] * sin + current[1] * cos;
            dir = new double[]{ newX, newZ };
            skateDir.put(id, dir);
        }

        // ── Apply velocity ────────────────────────────────────────────────────
        double dx = dir[0] * speed;
        double dz = dir[1] * speed;
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(dx, current.y, dz);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // Store state for next tick's ricochet check
        prevPos.put(id, curPos);
        prevExpected.put(id, new double[]{ dx, dz });

        // ── Collision damage ──────────────────────────────────────────────────
        if (speed >= HIT_SPEED_MIN) {
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();

            List<LivingEntity> targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(0.2),
                    e -> e != player
            );
            for (LivingEntity target : targets) {
                long lastHit = hitCooldown.getOrDefault(target.getUUID(), 0L);
                if (now - lastHit >= HIT_COOLDOWN) {
                    target.hurt(level.damageSources().playerAttack(player), HIT_DAMAGE);
                    hitCooldown.put(target.getUUID(), now);
                }
            }
            if (player.tickCount % 100 == 0) {
                hitCooldown.entrySet().removeIf(e -> now - e.getValue() > 60);
            }
        }
    }

    // ── Logout cleanup ────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        cleanup(event.getEntity().getUUID());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void cleanup(UUID id) {
        skateSpeed.remove(id);
        skateDir.remove(id);
        ricTimer.remove(id);
        prevPos.remove(id);
        prevExpected.remove(id);
    }

    /** Returns a normalised [dirX, dirZ] from a Minecraft yaw (degrees). */
    private static double[] dirFromYaw(float yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        return new double[]{ -Math.sin(yaw), Math.cos(yaw) };
    }
}
