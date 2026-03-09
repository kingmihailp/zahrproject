package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * Handles the four elemental blade enchantment effects triggered on melee hit:
 *
 *   Лезвие воды  — water particle stream + upward launch (+1.5 Y)
 *   Лезвие огня  — flame ring particles + 15-second fire (extinguished by water)
 *   Лезвие земли — block particles + thrown gravel + confusion + knockback
 *   Лезвие ветра — tornado spiral particles + strong upward launch (+2.0 Y)
 */
public class BladeEnchantmentsHandler {

    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() == null) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        LivingEntity target = event.getEntity();
        ItemStack weapon    = attacker.getMainHandItem();

        if (EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.WATER_BLADE.get(), weapon) > 0)
            applyWaterBlade(level, target, attacker);

        if (EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.FIRE_BLADE.get(), weapon) > 0)
            applyFireBlade(level, target);

        if (EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.EARTH_BLADE.get(), weapon) > 0)
            applyEarthBlade(level, target);

        if (EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.WIND_BLADE.get(), weapon) > 0)
            applyWindBlade(level, target);
    }

    // ── Water Blade ────────────────────────────────────────────────────────────

    private static void applyWaterBlade(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        // Particle stream from attacker eye to target centre
        Vec3 from = attacker.getEyePosition();
        Vec3 to   = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 dir  = to.subtract(from);
        double dist = dir.length();
        if (dist > 0.01) dir = dir.normalize();
        int steps = (int) Math.min(dist * 5, 20);
        for (int i = 0; i < steps; i++) {
            double t = i / (double) steps;
            level.sendParticles(ParticleTypes.DRIPPING_WATER,
                    from.x + dir.x * t * dist,
                    from.y + dir.y * t * dist,
                    from.z + dir.z * t * dist,
                    2, 0.08, 0.08, 0.08, 0.0);
        }

        // Splash ring around target
        double cx = target.getX(), cy = target.getY() + 0.5, cz = target.getZ();
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI * 2 / 12;
            level.sendParticles(ParticleTypes.SPLASH,
                    cx + Math.cos(angle), cy, cz + Math.sin(angle),
                    4, 0.1, 0.2, 0.1, 0.15);
        }

        // Launch upward
        Vec3 vel = target.getDeltaMovement();
        target.setDeltaMovement(vel.x, vel.y + 1.5, vel.z);
        target.hurtMarked = true;

        level.playSound(null, target.blockPosition(),
                SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // ── Fire Blade ─────────────────────────────────────────────────────────────

    private static void applyFireBlade(ServerLevel level, LivingEntity target) {
        // 15 seconds of fire; vanilla extinguishes it when the entity enters water
        target.setSecondsOnFire(15);

        // Ring of flame + smoke particles around the target
        double cx = target.getX(), cy = target.getY() + 0.3, cz = target.getZ();
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2 / 16;
            double px = cx + Math.cos(angle) * 0.9;
            double pz = cz + Math.sin(angle) * 0.9;
            level.sendParticles(ParticleTypes.FLAME, px, cy,       pz, 3, 0.05, 0.25, 0.05, 0.03);
            level.sendParticles(ParticleTypes.SMOKE,  px, cy + 0.4, pz, 2, 0.05, 0.15, 0.05, 0.02);
        }

        level.playSound(null, target.blockPosition(),
                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // ── Earth Blade ────────────────────────────────────────────────────────────

    private static void applyEarthBlade(ServerLevel level, LivingEntity target) {
        // Confusion + slowness to disorient
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION,        100, 0, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));

        // Random lateral knockback + slight upward push
        Vec3 vel = target.getDeltaMovement();
        target.setDeltaMovement(
                vel.x + (RANDOM.nextDouble() - 0.5) * 0.6,
                vel.y + 0.6,
                vel.z + (RANDOM.nextDouble() - 0.5) * 0.6);
        target.hurtMarked = true;

        // Block-break particle bursts (dirt + stone)
        double cx = target.getX(), cy = target.getY(), cz = target.getZ();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                cx, cy + 0.5, cz, 20, 1.5, 0.5, 1.5, 0.2);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                cx, cy + 0.5, cz, 15, 1.5, 0.5, 1.5, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION, cx, cy + 0.5, cz, 3, 1.0, 0.3, 1.0, 0.0);

        // Throw 4 gravel blocks upward as physical projectiles
        for (int i = 0; i < 4; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 3;
            double oz = (RANDOM.nextDouble() - 0.5) * 3;
            FallingBlockEntity fbe = new FallingBlockEntity(
                    level, cx + ox, cy + 0.5, cz + oz,
                    Blocks.GRAVEL.defaultBlockState());
            fbe.setDeltaMovement(
                    (RANDOM.nextDouble() - 0.5) * 0.4,
                    1.5 + RANDOM.nextDouble() * 0.5,
                    (RANDOM.nextDouble() - 0.5) * 0.4);
            fbe.dropItem = false;
            level.addFreshEntity(fbe);
        }

        level.playSound(null, target.blockPosition(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    // ── Wind Blade ─────────────────────────────────────────────────────────────

    private static void applyWindBlade(ServerLevel level, LivingEntity target) {
        // Strong upward launch + slight random horizontal
        Vec3 vel = target.getDeltaMovement();
        target.setDeltaMovement(
                vel.x + (RANDOM.nextDouble() - 0.5) * 0.6,
                vel.y + 2.0,
                vel.z + (RANDOM.nextDouble() - 0.5) * 0.6);
        target.hurtMarked = true;

        // Spiral tornado: 3 full rotations rising 3.5 blocks
        double cx = target.getX(), cz = target.getZ();
        for (int i = 0; i < 24; i++) {
            double t      = i / 23.0;                          // 0..1
            double angle  = i * Math.PI * 2.0 * 3.0 / 24.0;  // 3 rotations
            double radius = 0.3 + t * 1.2;
            double px = cx + Math.cos(angle) * radius;
            double py = target.getY() + t * 3.5;
            double pz = cz + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.CLOUD, px, py, pz, 1, 0.1, 0.1, 0.1, 0.0);
            level.sendParticles(ParticleTypes.SMOKE,  px, py, pz, 1, 0.05, 0.05, 0.05, 0.01);
        }

        level.playSound(null, target.blockPosition(),
                SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.0f, 1.2f);
    }
}
