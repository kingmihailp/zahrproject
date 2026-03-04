package com.zahrproject.votingmod.entity;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * "Черные дыры" event entity.
 *
 * Visual:
 *   • Rotating, scaling BlockDisplay (coal block) as the black body.
 *   • Thin PORTAL accretion disk in the equatorial plane.
 *   • SMOKE tendrils pulled from 3-D sphere toward center.
 *   • LARGE_SMOKE at core.
 *
 * Mechanics:
 *   • Grows every tick; all force/destruction scales with size.
 *   • Entities are pulled toward center; killed ONLY when they reach
 *     the very center (dist ≤ 1.5).
 *   • Blocks destroyed in two concentric spherical zones every 2 ticks.
 *   • Bedrock / command blocks immune.
 *   • Nether Star item near core → massive explosion.
 */
public class BlackHoleEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);

    private static final Random RNG = new Random();

    private int age = 0;
    private UUID displayUUID = null; // the BlockDisplay entity

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

    public float getHoleSize()       { return this.entityData.get(DATA_SIZE); }
    private void setHoleSize(float s) { this.entityData.set(DATA_SIZE, s); }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        age++;

        if (this.level().isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) this.level();
        float size = getHoleSize();

        // 1. Grow
        setHoleSize(size + 0.01f);

        Vec3 center = this.position();

        // 2. Spawn / update BlockDisplay visual
        updateBlockDisplay(serverLevel, center, size);

        // 3. Particles (accretion disk + smoke tendrils)
        spawnParticles(serverLevel, center, size);

        // 4. Pull entities; kill only at very center
        double pullRadius  = size * 15.0;
        double killRadius  = 1.5;          // fixed — only the very center
        double damageRadius = size * 2.0;  // mild damage warning zone

        List<Entity> nearby = serverLevel.getEntities(this,
                new AABB(center, center).inflate(pullRadius));

        for (Entity entity : nearby) {
            if (entity == this) continue;

            Vec3 delta = center.subtract(entity.position());
            double dist = Math.max(delta.length(), 0.01);

            // Nether Star near center → explode
            if (entity instanceof ItemEntity item && item.getItem().is(Items.NETHER_STAR)) {
                if (dist <= killRadius + 1.0) {
                    explode(serverLevel);
                    return;
                }
            }

            // Reached the very center → instant death
            if (dist <= killRadius) {
                if (entity instanceof LivingEntity living) {
                    living.hurt(serverLevel.damageSources().magic(), Float.MAX_VALUE);
                } else {
                    entity.discard();
                }
                continue;
            }

            // Damage warning zone (but no instant kill)
            if (entity instanceof LivingEntity living && dist <= damageRadius) {
                living.hurt(serverLevel.damageSources().magic(), size * 2.0f);
            }

            // Pull force: F = size² * k / dist²  (capped)
            double force = Math.min((size * size * 0.20) / (dist * dist), 6.0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(delta.normalize().scale(force)));
        }

        // 5. Destroy blocks every 2 ticks (scales with size)
        if (age % 2 == 0) {
            destroyBlocks(serverLevel, size);
        }
    }

    // ── BlockDisplay ─────────────────────────────────────────────────────────

    private void updateBlockDisplay(ServerLevel level, Vec3 center, float size) {
        // Lazy-spawn
        if (displayUUID == null) {
            Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            display.setPos(center.x, center.y, center.z);
            display.setBlockState(Blocks.COAL_BLOCK.defaultBlockState());
            display.setNoGravity(true);
            display.setInvisible(false);
            display.setInterpolationDuration(2);
            level.addFreshEntity(display);
            displayUUID = display.getUUID();
        }

        Entity raw = level.getEntity(displayUUID);
        if (!(raw instanceof Display.BlockDisplay bd)) return;

        // Keep display co-located with the black hole
        bd.setPos(center.x, center.y, center.z);

        // Rotating cube: dual-axis spin + scale matching hole size
        float scale  = size * 2.0f;
        float yAngle = (float) Math.toRadians(age * 3.0);
        float xAngle = (float) Math.toRadians(age * 1.7);

        Quaternionf rotation = new Quaternionf().rotateY(yAngle).rotateX(xAngle);

        bd.setTransformation(new Transformation(
                new Vector3f(-scale * 0.5f, -scale * 0.5f, -scale * 0.5f), // center the cube
                rotation,
                new Vector3f(scale, scale, scale),
                new Quaternionf()                                            // right rotation identity
        ));
        bd.setInterpolationDelay(0);
    }

    // ── Block destruction ────────────────────────────────────────────────────

    private void destroyBlocks(ServerLevel level, float size) {
        // Inner zone: quadratic scaling (most blocks eaten here)
        int innerCount = Math.max(4, (int) (size * size * 3));
        destroyRandom(level, this.blockPosition(), 0, size * 3.0, innerCount);

        // Outer zone: linear scaling
        int outerCount = Math.max(2, (int) (size * 20));
        destroyRandom(level, this.blockPosition(), size * 3.0, Math.min(size * 8.0, 80.0), outerCount);
    }

    private void destroyRandom(ServerLevel level, BlockPos center,
                                double minR, double maxR, int count) {
        for (int i = 0; i < count; i++) {
            double r     = minR + (maxR - minR) * Math.cbrt(RNG.nextDouble());
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

        // Thin PORTAL accretion disk — compact ring around the coal block
        int diskCount = (int) (size * 20);
        for (int i = 0; i < diskCount; i++) {
            double a = age * 0.05 + i * (2 * Math.PI / diskCount);
            double r = size * (1.2 + RNG.nextDouble() * 1.8);        // tighter disk
            double px = c.x + Math.cos(a) * r;
            double py = c.y + (RNG.nextDouble() - 0.5) * size * 0.15; // very thin
            double pz = c.z + Math.sin(a) * r;
            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 0,
                    (c.x - px) * 0.07, 0.0, (c.z - pz) * 0.07, 1.0);
        }

        // 3-D SMOKE tendrils — pulled from all directions
        int tendrilCount = (int) (size * 16);
        for (int i = 0; i < tendrilCount; i++) {
            double[] p = spherePoint(c, size * (2.5 + RNG.nextDouble() * 4.5));
            level.sendParticles(ParticleTypes.SMOKE, p[0], p[1], p[2], 0,
                    (c.x - p[0]) * 0.05, (c.y - p[1]) * 0.05, (c.z - p[2]) * 0.05, 1.0);
        }

        // LARGE_SMOKE at core
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                c.x, c.y, c.z,
                Math.max(1, (int) (size * 3)), 0.0, 0.0, 0.0, 0.02);
    }

    /** Uniform random point on a sphere surface of radius {@code r} at {@code c}. */
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

    // ── Remove: also discard the display entity ───────────────────────────────

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && displayUUID != null) {
            Entity display = ((ServerLevel) this.level()).getEntity(displayUUID);
            if (display != null) display.discard();
        }
        super.remove(reason);
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setHoleSize(tag.getFloat("BHSize"));
        age = tag.getInt("BHAge");
        if (tag.hasUUID("BHDisplay")) {
            displayUUID = tag.getUUID("BHDisplay");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("BHSize", getHoleSize());
        tag.putInt("BHAge", age);
        if (displayUUID != null) {
            tag.putUUID("BHDisplay", displayUUID);
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public boolean isPickable()     { return false; }
    @Override public boolean isPushable()     { return false; }
    @Override public boolean isInvulnerable() { return true;  }
}
