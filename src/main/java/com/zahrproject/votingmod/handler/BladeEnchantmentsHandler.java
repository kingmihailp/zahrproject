package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the four elemental blade enchantment effects triggered on melee hit:
 *
 *   Лезвие воды  — water particles + slow drowning for 15 s (1 drown dmg/s)
 *   Лезвие огня  — flame ring + 15-second fire (extinguished by water)
 *   Лезвие земли — block particles + FallingBlocks + entity frozen in place ~2 s
 *   Лезвие ветра — 3-second tornado orbiting mob, then strong upward launch
 */
public class BladeEnchantmentsHandler {

    private static final Random RANDOM = new Random();

    // ── Active drowning (Water Blade): ticks remaining per entity ─────────────
    private static final int DROWN_TICKS = 300; // 15 seconds
    private static final Map<LivingEntity, Integer> DROWNING_ENTITIES = new ConcurrentHashMap<>();

    // ── Active freeze (Earth Blade): ticks remaining per entity ───────────────
    private static final int FREEZE_TICKS = 40; // 2 seconds
    private static final Map<LivingEntity, Integer> FROZEN_ENTITIES = new ConcurrentHashMap<>();

    // ── Active tornado (Wind Blade) ────────────────────────────────────────────
    private static final int TORNADO_TICKS = 60; // 3 seconds
    private static final Map<LivingEntity, TornadoData> ACTIVE_TORNADOES = new ConcurrentHashMap<>();

    private static final class TornadoData {
        final double centerX, centerZ;
        int ticksLeft = TORNADO_TICKS;

        TornadoData(double centerX, double centerZ) {
            this.centerX = centerX;
            this.centerZ = centerZ;
        }
    }

    // ── Attack event ──────────────────────────────────────────────────────────

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

    // ── Server tick ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // ── Drowning (Water Blade) ────────────────────────────────────────────
        if (!DROWNING_ENTITIES.isEmpty()) {
            Iterator<Map.Entry<LivingEntity, Integer>> it = DROWNING_ENTITIES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LivingEntity, Integer> entry = it.next();
                LivingEntity e    = entry.getKey();
                int          ticks = entry.getValue() - 1;
                if (!e.isAlive()) { it.remove(); continue; }

                entry.setValue(ticks);

                // Drain air supply to show the drowning bar
                e.setAirSupply(0);

                // 1 drown damage per second (every 20 ticks)
                if (ticks % 20 == 0 && e.level() instanceof ServerLevel sl) {
                    e.hurt(sl.damageSources().drown(), 1.0f);
                }

                // Drip particles each second
                if (ticks % 20 == 0 && e.level() instanceof ServerLevel sl) {
                    double cx = e.getX(), cy = e.getY() + e.getBbHeight() * 0.5, cz = e.getZ();
                    sl.sendParticles(ParticleTypes.DRIPPING_WATER,
                            cx, cy, cz, 12, 0.4, 0.5, 0.4, 0.0);
                }

                if (ticks <= 0) {
                    // Restore air supply when effect ends
                    e.setAirSupply(e.getMaxAirSupply());
                    it.remove();
                }
            }
        }

        // ── Freeze (Earth Blade) ──────────────────────────────────────────────
        if (!FROZEN_ENTITIES.isEmpty()) {
            Iterator<Map.Entry<LivingEntity, Integer>> it = FROZEN_ENTITIES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LivingEntity, Integer> entry = it.next();
                LivingEntity e     = entry.getKey();
                int          ticks = entry.getValue() - 1;
                if (!e.isAlive()) { it.remove(); continue; }

                entry.setValue(ticks);

                // Cancel all horizontal movement; allow only gravity
                Vec3 vel = e.getDeltaMovement();
                e.setDeltaMovement(0, Math.min(vel.y, 0), 0);
                e.hurtMarked = true;

                if (ticks <= 0) it.remove();
            }
        }

        // ── Tornado (Wind Blade) ──────────────────────────────────────────────
        if (!ACTIVE_TORNADOES.isEmpty()) {
            Iterator<Map.Entry<LivingEntity, TornadoData>> it = ACTIVE_TORNADOES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LivingEntity, TornadoData> entry = it.next();
                LivingEntity e    = entry.getKey();
                TornadoData  data = entry.getValue();

                if (!e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                    it.remove();
                    continue;
                }

                data.ticksLeft--;

                double progress = 1.0 - data.ticksLeft / (double) TORNADO_TICKS;
                double angle    = progress * Math.PI * 2.0 * 5.0; // 5 rotations
                double radius   = 1.4;

                double px = data.centerX + Math.cos(angle) * radius;
                double pz = data.centerZ + Math.sin(angle) * radius;
                double py = e.getY() + 0.08; // slow rise

                if (e instanceof ServerPlayer sp) {
                    sp.connection.teleport(px, py, pz, sp.getYRot(), sp.getXRot());
                } else {
                    e.setPos(px, py, pz);
                    e.setDeltaMovement(0, 0, 0);
                    e.hurtMarked = true;
                }

                if (data.ticksLeft % 2 == 0) {
                    spawnTornadoRing(level, px, py, pz, angle, radius);
                }

                if (data.ticksLeft <= 0) {
                    e.setDeltaMovement(
                            (RANDOM.nextDouble() - 0.5) * 0.3,
                            3.5,
                            (RANDOM.nextDouble() - 0.5) * 0.3);
                    e.hurtMarked = true;
                    level.playSound(null, BlockPos.containing(px, py, pz),
                            SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.2f, 0.7f);
                    it.remove();
                }
            }
        }
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

        // Start drowning — resets timer if already active
        DROWNING_ENTITIES.put(target, DROWN_TICKS);
        target.setAirSupply(0);

        level.playSound(null, target.blockPosition(),
                SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // ── Fire Blade ─────────────────────────────────────────────────────────────

    private static void applyFireBlade(ServerLevel level, LivingEntity target) {
        target.setSecondsOnFire(15);

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
        // Stop the entity: zero out velocity, apply Slowness to prevent walking
        target.setDeltaMovement(0, 0, 0);
        target.hurtMarked = true;
        // Slowness amplifier 6 (level VII) clamps movement speed to ~0
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_TICKS, 6, false, true));

        // Register for per-tick velocity cancellation (mobs fight AI otherwise)
        FROZEN_ENTITIES.put(target, FREEZE_TICKS);

        // Block-break particle bursts
        double cx = target.getX(), cy = target.getY(), cz = target.getZ();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                cx, cy + 0.5, cz, 20, 1.5, 0.5, 1.5, 0.2);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                cx, cy + 0.5, cz, 15, 1.5, 0.5, 1.5, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION, cx, cy + 0.5, cz, 3, 1.0, 0.3, 1.0, 0.0);

        // Throw up to 4 real ground blocks upward
        BlockPos center = target.blockPosition();
        int thrown = 0;
        outer:
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (thrown >= 4) break outer;
                BlockPos pos = center.offset(dx, -1, dz);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.BEDROCK)) continue;
                if (state.getDestroySpeed(level, pos) < 0) continue;
                if (RANDOM.nextFloat() > 0.5f) continue;
                FallingBlockEntity fbe = FallingBlockEntity.fall(level, pos, state);
                fbe.setDeltaMovement(
                        (RANDOM.nextDouble() - 0.5) * 0.5,
                        1.5 + RANDOM.nextDouble() * 0.5,
                        (RANDOM.nextDouble() - 0.5) * 0.5);
                fbe.dropItem = false;
                thrown++;
            }
        }

        level.playSound(null, target.blockPosition(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    // ── Wind Blade ─────────────────────────────────────────────────────────────

    private static void applyWindBlade(ServerLevel level, LivingEntity target) {
        if (ACTIVE_TORNADOES.containsKey(target)) return;

        spawnTornadoRing(level, target.getX(), target.getY(), target.getZ(), 0, 1.4);
        level.playSound(null, target.blockPosition(),
                SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.0f, 0.6f);

        ACTIVE_TORNADOES.put(target, new TornadoData(target.getX(), target.getZ()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void spawnTornadoRing(ServerLevel level,
                                         double cx, double cy, double cz,
                                         double baseAngle, double radius) {
        for (int i = 0; i < 8; i++) {
            double a  = baseAngle + i * Math.PI * 2 / 8;
            double px = cx + Math.cos(a) * radius;
            double pz = cz + Math.sin(a) * radius;
            level.sendParticles(ParticleTypes.CLOUD, px, cy, pz, 1, 0.05, 0.15, 0.05, 0.0);
            level.sendParticles(ParticleTypes.SMOKE,  px, cy, pz, 1, 0.05, 0.05, 0.05, 0.01);
        }
        for (int i = 0; i < 5; i++) {
            double a  = baseAngle + i * Math.PI * 2 / 5 + Math.PI / 5;
            double px = cx + Math.cos(a) * (radius + 0.7);
            double pz = cz + Math.sin(a) * (radius + 0.7);
            level.sendParticles(ParticleTypes.CLOUD, px, cy + 0.4, pz, 1, 0.1, 0.2, 0.1, 0.0);
        }
    }
}
