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

/**
 * "Черные дыры" event entity.
 *
 * Behaviour (server-side only):
 *   • Grows indefinitely, pulling nearby entities and destroying blocks.
 *   • Entities inside the core take lethal damage.
 *   • Bedrock and command blocks are immune.
 *   • A dropped Nether Star that reaches the core triggers an explosion.
 */
public class BlackHoleEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);

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

    public float getHoleSize() { return this.entityData.get(DATA_SIZE); }
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
        setHoleSize(size + 0.004f);

        Vec3 center = this.position();

        // 2. Particles (visual)
        spawnParticles(serverLevel, center, size);

        // 3. Pull & damage entities
        double pullRadius = size * 10.0;
        double coreRadius = size * 1.0;
        double damageRadius = size * 2.5;

        List<Entity> nearby = serverLevel.getEntities(this,
                new AABB(center, center).inflate(pullRadius));

        for (Entity entity : nearby) {
            if (entity == this) continue;

            Vec3 delta = center.subtract(entity.position());
            double dist = Math.max(delta.length(), 0.01);

            // Nether Star inside pull radius → explode
            if (entity instanceof ItemEntity item && item.getItem().is(Items.NETHER_STAR)) {
                if (dist <= coreRadius + 1.5) {
                    explode(serverLevel);
                    return;
                }
            }

            // Core: lethal
            if (dist <= coreRadius) {
                if (entity instanceof LivingEntity living) {
                    living.hurt(serverLevel.damageSources().magic(), Float.MAX_VALUE);
                } else {
                    entity.discard();
                }
                continue;
            }

            // Damage zone
            if (entity instanceof LivingEntity living && dist <= damageRadius) {
                living.hurt(serverLevel.damageSources().magic(), size * 3.0f);
            }

            // Pull force: scales with size², falls off with dist²
            double force = Math.min((size * size * 0.08) / (dist * dist), 3.0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(delta.normalize().scale(force)));
        }

        // 4. Destroy surrounding blocks (every 4 ticks)
        if (age % 4 == 0) {
            destroyBlocks(serverLevel, size);
        }
    }

    // ── Block destruction ────────────────────────────────────────────────────

    private void destroyBlocks(ServerLevel level, float size) {
        double radius = Math.min(size * 4.0, 64.0);
        int count = Math.max(2, (int) (size * 10));
        BlockPos center = this.blockPosition();

        for (int i = 0; i < count; i++) {
            // Uniform random point in sphere
            double r = radius * Math.cbrt(Math.random());
            double theta = Math.random() * 2 * Math.PI;
            double phi = Math.acos(2 * Math.random() - 1);

            int dx = (int) (r * Math.sin(phi) * Math.cos(theta));
            int dy = (int) (r * Math.cos(phi));
            int dz = (int) (r * Math.sin(phi) * Math.sin(theta));

            BlockPos pos = center.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) continue;
            if (isImmune(state)) continue;

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

    private void spawnParticles(ServerLevel level, Vec3 center, float size) {
        // Dark core: SQUID_INK spiraling inward
        int coreCount = (int) (size * 8);
        for (int i = 0; i < coreCount; i++) {
            double a = Math.random() * 2 * Math.PI;
            double r = size * (0.3 + Math.random() * 0.9);
            double px = center.x + Math.cos(a) * r;
            double py = center.y + (Math.random() - 0.5) * r;
            double pz = center.z + Math.sin(a) * r;
            double vx = (center.x - px) * 0.08;
            double vy = (center.y - py) * 0.08;
            double vz = (center.z - pz) * 0.08;
            level.sendParticles(ParticleTypes.SQUID_INK, px, py, pz, 0, vx, vy, vz, 1.0);
        }

        // Outer swirl: PORTAL
        int swirlCount = (int) (size * 16);
        for (int i = 0; i < swirlCount; i++) {
            double a = age * 0.04 + i * (2 * Math.PI / swirlCount);
            double r = size * (2.0 + Math.random() * 3.0);
            double px = center.x + Math.cos(a) * r;
            double py = center.y + (Math.random() - 0.5) * size * 0.8;
            double pz = center.z + Math.sin(a) * r;
            double vx = (center.x - px) * 0.04;
            double vz = (center.z - pz) * 0.04;
            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 0, vx, 0, vz, 1.0);
        }

        // Large smoke at center
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                center.x, center.y, center.z,
                (int) (size * 2), size * 0.4, size * 0.4, size * 0.4, 0.01);
    }

    // ── Explosion ────────────────────────────────────────────────────────────

    private void explode(ServerLevel level) {
        Vec3 pos = this.position();
        float power = Math.max(getHoleSize() * 4.0f, 10.0f);
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

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isInvulnerable() { return true; }
}
