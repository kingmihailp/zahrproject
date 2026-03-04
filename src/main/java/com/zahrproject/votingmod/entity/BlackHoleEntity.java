package com.zahrproject.votingmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.Random;

/**
 * "Черные дыры" event entity.
 *
 * Behaviour (server-side only):
 *   • Grows indefinitely; all destruction scales with size.
 *   • Pulls entities with force ∝ size² / dist².
 *   • Entities inside the event horizon take lethal damage.
 *   • Blocks destroyed every 2 ticks: aggressively inside, moderately in outer band.
 *   • Bedrock and command blocks are immune.
 *   • Nether Star item within striking range → giant explosion.
 *
 * Visual:
 *   • SQUID_INK sphere shell — the round black body.
 *   • PORTAL flat accretion disk — rotating ring in the equatorial plane.
 *   • SMOKE tendrils — pulled from all directions toward center.
 *   • LARGE_SMOKE at the core.
 */
public class BlackHoleEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);

    private static final Random RNG = new Random();

    private int age = 0;

    // ── Construction ─────────────────────────────────────────────────────────

    public BlackHoleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static BlackHoleEntity create(Level level, double x, double y, double z) {
        BlackHoleEntity bh = new BlackHoleEntity(ModEntities.BLACK_HOLE.get(), level);
        bh.setPos(x, y, z);
        return bh;
    }

    // ── Data sync ────────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SIZE, 1.5f);
    }

    public float getHoleSize()      { return this.entityData.get(DATA_SIZE); }
    private void setHoleSize(float s) { this.entityData.set(DATA_SIZE, s); }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        age++;

        if (this.level().isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) this.level();
        float size = getHoleSize();

        // 1. Grow — faster over time because size itself grows
        setHoleSize(size + 0.01f);

        Vec3 center = this.position();

        // 2. Particles
        spawnParticles(serverLevel, center, size);

        // 3. Pull & damage entities
        double pullRadius  = size * 15.0;
        double coreRadius  = size * 1.0;
        double damageRadius = size * 3.0;

        List<Entity> nearby = serverLevel.getEntities(this,
                new AABB(center, center).inflate(pullRadius));

        for (Entity entity : nearby) {
            if (entity == this) continue;

            Vec3 delta = center.subtract(entity.position());
            double dist = Math.max(delta.length(), 0.01);

            // Nether Star → explode
            if (entity instanceof ItemEntity item && item.getItem().is(Items.NETHER_STAR)) {
                if (dist <= coreRadius + 2.0) {
                    explode(serverLevel);
                    return;
                }
            }

            // Event horizon: instant death / discard
            if (dist <= coreRadius) {
                if (entity instanceof LivingEntity living) {
                    living.hurt(serverLevel.damageSources().magic(), Float.MAX_VALUE);
                } else {
                    entity.discard();
                }
                continue;
            }

            // Damage zone: heavy continuous damage
            if (entity instanceof LivingEntity living && dist <= damageRadius) {
                living.hurt(serverLevel.damageSources().magic(), size * 5.0f);
            }

            // Pull force: F = size² * k / dist²   (capped to prevent teleport glitch)
            double force = Math.min((size * size * 0.20) / (dist * dist), 6.0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(delta.normalize().scale(force)));
        }

        // 4. Destroy blocks — every 2 ticks; both inner and outer bands
        if (age % 2 == 0) {
            destroyBlocks(serverLevel, size);
        }
    }

    // ── Block destruction ────────────────────────────────────────────────────

    private void destroyBlocks(ServerLevel level, float size) {
        BlockPos center = this.blockPosition();

        // Inner zone (0 → size*3): very aggressive — scales quadratically with size
        int innerCount = Math.max(4, (int) (size * size * 3));
        destroyRandom(level, center, 0, size * 3.0, innerCount);

        // Outer zone (size*3 → size*8): moderate — scales linearly
        int outerCount = Math.max(2, (int) (size * 20));
        destroyRandom(level, center, size * 3.0, Math.min(size * 8.0, 80.0), outerCount);
    }

    /**
     * Pick {@code count} random block positions uniformly within a spherical shell
     * [minR, maxR] and destroy non-immune blocks.
     */
    private void destroyRandom(ServerLevel level, BlockPos center,
                                double minR, double maxR, int count) {
        for (int i = 0; i < count; i++) {
            double r = minR + (maxR - minR) * Math.cbrt(RNG.nextDouble());
            double phi   = Math.acos(2 * RNG.nextDouble() - 1);
            double theta = RNG.nextDouble() * 2 * Math.PI;

            int dx = (int) (r * Math.sin(phi) * Math.cos(theta));
            int dy = (int) (r * Math.cos(phi));
            int dz = (int) (r * Math.sin(phi) * Math.sin(theta));

            BlockPos pos = center.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || isImmune(state)) continue;

            level.destroyBlock(pos, false);
        }
    }

    private static boolean isImmune(BlockState state) {
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW);
    }

    // ── Particles ────────────────────────────────────────────────────────────

    private void spawnParticles(ServerLevel level, Vec3 c, float size) {

        // ── Layer 1: SQUID_INK sphere shell — the round black body ───────────
        //   Uniform on sphere surface via spherical coordinates.
        int sphereCount = (int) (size * 24);
        for (int i = 0; i < sphereCount; i++) {
            double[] p = spherePoint(c, size * (0.85 + RNG.nextDouble() * 0.3));
            double vx = (c.x - p[0]) * 0.06;
            double vy = (c.y - p[1]) * 0.06;
            double vz = (c.z - p[2]) * 0.06;
            level.sendParticles(ParticleTypes.SQUID_INK, p[0], p[1], p[2], 0, vx, vy, vz, 1.0);
        }

        // ── Layer 2: PORTAL accretion disk — flat rotating ring ──────────────
        //   Disk lies in the XZ plane (realistic black hole look).
        int diskCount = (int) (size * 30);
        for (int i = 0; i < diskCount; i++) {
            double a = age * 0.05 + i * (2 * Math.PI / diskCount);
            double r = size * (1.6 + RNG.nextDouble() * 4.0);
            double px = c.x + Math.cos(a) * r;
            double py = c.y + (RNG.nextDouble() - 0.5) * size * 0.25; // very flat
            double pz = c.z + Math.sin(a) * r;
            double vx = (c.x - px) * 0.07;
            double vz = (c.z - pz) * 0.07;
            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 0, vx, 0.0, vz, 1.0);
        }

        // ── Layer 3: SMOKE tendrils — pulled from all directions ─────────────
        //   Full 3D sphere of incoming smoke particles (gives "eating" look).
        int tendrilCount = (int) (size * 18);
        for (int i = 0; i < tendrilCount; i++) {
            double[] p = spherePoint(c, size * (3.0 + RNG.nextDouble() * 5.0));
            double vx = (c.x - p[0]) * 0.05;
            double vy = (c.y - p[1]) * 0.05;
            double vz = (c.z - p[2]) * 0.05;
            level.sendParticles(ParticleTypes.SMOKE, p[0], p[1], p[2], 0, vx, vy, vz, 1.0);
        }

        // ── Layer 4: LARGE_SMOKE at the core ─────────────────────────────────
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                c.x, c.y, c.z,
                Math.max(1, (int) (size * 3)), 0.0, 0.0, 0.0, 0.02);
    }

    /**
     * Random point on a sphere surface of radius {@code r} centered at {@code c}.
     * Uses the uniform spherical distribution: phi = acos(2U-1), theta = 2πV.
     */
    private static double[] spherePoint(Vec3 c, double r) {
        double phi   = Math.acos(2 * RNG.nextDouble() - 1);
        double theta = RNG.nextDouble() * 2 * Math.PI;
        return new double[]{
                c.x + r * Math.sin(phi) * Math.cos(theta),
                c.y + r * Math.cos(phi),
                c.z + r * Math.sin(phi) * Math.sin(theta)
        };
    }

    // ── Explosion ────────────────────────────────────────────────────────────

    private void explode(ServerLevel level) {
        Vec3 pos = this.position();
        float power = Math.max(getHoleSize() * 5.0f, 12.0f);
        level.explode(this, pos.x, pos.y, pos.z, power, Level.ExplosionInteraction.TNT);
        this.discard();
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setHoleSize(tag.getFloat("BHSize"));
        age = tag.getInt("BHAge");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("BHSize", getHoleSize());
        tag.putInt("BHAge", age);
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public boolean isPickable()    { return false; }
    @Override public boolean isPushable()    { return false; }
    @Override public boolean isInvulnerable() { return true; }
}
