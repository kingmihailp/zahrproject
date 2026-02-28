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
 * Handles the Skateboard enchantment movement logic.
 *
 * Conditions for activation:
 *   - Player holds a Shield enchanted with "Скейтборд" in the offhand
 *   - Player is sprinting (default Ctrl key in Minecraft)
 *
 * Behaviour:
 *   - Speed builds up each tick while sprinting (acceleration)
 *   - Speed bleeds off each tick when not sprinting (deceleration)
 *   - Movement direction follows the player's horizontal look direction
 *   - Colliding with a mob or player at sufficient speed deals 2 hearts of damage
 */
public class SkateboardHandler {

    private static final double ACCELERATION      = 0.015; // blocks/tick added per tick
    private static final double DECELERATION      = 0.008; // blocks/tick removed per tick
    private static final double MAX_SPEED         = 0.6;   // ~1.2× normal sprint speed
    private static final double HIT_SPEED_MIN     = 0.15;  // minimum speed to deal collision damage
    private static final float  HIT_DAMAGE        = 4.0f;  // 2 hearts
    private static final long   HIT_COOLDOWN_TICKS = 20L;  // 1 second per target

    /** Per-player current skateboard speed (blocks/tick). */
    private static final Map<UUID, Double> skateSpeed  = new HashMap<>();

    /** Last game-tick a given entity was hit by skateboard collision. */
    private static final Map<UUID, Long>   hitCooldown = new HashMap<>();

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
            skateSpeed.remove(id);
            return;
        }

        // Ctrl in Minecraft default keybinds = sprint
        boolean accelerating = player.isSprinting();

        double speed = skateSpeed.getOrDefault(id, 0.0);
        if (accelerating) {
            speed = Math.min(speed + ACCELERATION, MAX_SPEED);
        } else {
            speed = Math.max(speed - DECELERATION, 0.0);
        }

        if (speed < 0.001) {
            skateSpeed.remove(id);
            return;
        }
        skateSpeed.put(id, speed);

        // Horizontal look direction
        double yaw = Math.toRadians(player.getYRot());
        double dx  = -Math.sin(yaw) * speed;
        double dz  =  Math.cos(yaw) * speed;

        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(dx, current.y, dz);

        // Push updated velocity to the client immediately
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // ── Collision damage ───────────────────────────────────────────────────
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
                if (now - lastHit >= HIT_COOLDOWN_TICKS) {
                    target.hurt(level.damageSources().playerAttack(player), HIT_DAMAGE);
                    hitCooldown.put(target.getUUID(), now);
                }
            }

            // Periodic cleanup to prevent map from growing indefinitely
            if (player.tickCount % 100 == 0) {
                hitCooldown.entrySet().removeIf(e -> now - e.getValue() > 60);
            }
        }
    }

    /** Clean up speed state when a player disconnects. */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        skateSpeed.remove(event.getEntity().getUUID());
    }
}
