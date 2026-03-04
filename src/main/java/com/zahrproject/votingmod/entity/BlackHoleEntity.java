package com.zahrproject.votingmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * "Черные дыры" event entity.
 *
 * Visual:
 *   • Rotating, growing BlockDisplay (coal block) as the dark body,
 *     configured every tick via readAdditionalSaveData / NBT — the same
 *     path the /data command uses, avoiding any private-access issues.
 *   • Thin PORTAL accretion disk in the equatorial plane.
 *   • SMOKE tendrils pulled from a 3-D sphere toward the center.
 *   • LARGE_SMOKE at the core.
 *
 * Mechanics:
 *   • Grows every tick; all pull force and block destruction scale with size.
 *   • Entities pulled toward center; killed ONLY when they reach dist ≤ 1.5.
 *   • Blocks destroyed every 2 ticks in two concentric zones (quadratic scaling).
 *   • Bedrock / command blocks immune.
 *   • Nether Star near the core → massive explosion.
 */
public class BlackHoleEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);

    private static final Random RNG = new Random();

    /**
     * readAdditionalSaveData is protected — cache the Method once and call via
     * reflection. Walking up the class hierarchy finds whichever class declares it.
     */
    private static final Method READ_NBT;
    static {
        Method found = null;
        Class<?> cls = Display.BlockDisplay.class;
        while (cls != null && found == null) {
            try {
                found = cls.getDeclaredMethod("readAdditionalSaveData", CompoundTag.class);
            } catch (NoSuchMethodException ignored) {
                cls = cls.getSuperclass();
            }
        }
        if (found != null) found.setAccessible(true);
        READ_NBT = found;
    }

    /** Calls readAdditionalSaveData on a Display entity via the cached reflection Method. */
    private static void applyNbt(Entity entity, CompoundTag tag) {
        if (READ_NBT == null) return;
        try { READ_NBT.invoke(entity, tag); } catch (Exception ignored) {}
    }

    private int  age         = 0;
    private UUID displayUUID = null;

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

    public float getHoleSize()        { return this.entityData.get(DATA_SIZE); }
    private void setHoleSize(float s) { this.entityData.set(DATA_SIZE, s);    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        age++;

        if (this.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) this.level();
        float size = getHoleSize();

        // 1. Grow
        setHoleSize(size + 0.01f);

        Vec3 center = this.position();

        // 2. BlockDisplay visual
        updateBlockDisplay(level, center, size);

        // 3. Particles
        spawnParticles(level, center, size);

        // 4. Pull & kill at center
        double pullRadius   = size * 15.0;
        double killRadius   = 1.5;
        double damageRadius = size * 2.0;

        List<Entity> nearby = level.getEntities(this,
                new AABB(center, center).inflate(pullRadius));

        for (Entity entity : nearby) {
            if (entity == this) continue;

            Vec3 delta = center.subtract(entity.position());
            double dist = Math.max(delta.length(), 0.01);

            // Nether Star close to center → explosion
            if (entity instanceof ItemEntity item && item.getItem().is(Items.NETHER_STAR)) {
                if (dist <= killRadius + 2.0) { explode(level); return; }
            }

            // Reached the very center → instant death
            if (dist <= killRadius) {
                if (entity instanceof LivingEntity living)
                    living.hurt(level.damageSources().magic(), Float.MAX_VALUE);
                else
                    entity.discard();
                continue;
            }

            // Warning damage zone
            if (entity instanceof LivingEntity living && dist <= damageRadius)
                living.hurt(level.damageSources().magic(), size * 2.0f);

            // Pull: F = size² * k / dist²
            double force = Math.min((size * size * 0.20) / (dist * dist), 6.0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(delta.normalize().scale(force)));
        }

        // 5. Destroy blocks every 2 ticks
        if (age % 2 == 0) destroyBlocks(level, size);
    }

    // ── BlockDisplay ─────────────────────────────────────────────────────────

    /**
     * Spawns (once) and updates the BlockDisplay every tick via NBT —
     * the same code path the /data command uses, so no private-access issues.
     */
    private void updateBlockDisplay(ServerLevel level, Vec3 center, float size) {
        // Lazy-spawn
        if (displayUUID == null) {
            Display.BlockDisplay bd = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            bd.setPos(center.x, center.y, center.z);
            bd.setNoGravity(true);
            level.addFreshEntity(bd);
            displayUUID = bd.getUUID();

            // Set block state once via NBT
            CompoundTag initTag = new CompoundTag();
            CompoundTag blockStateTag = new CompoundTag();
            blockStateTag.putString("Name", "minecraft:coal_block");
            initTag.put("block_state", blockStateTag);
            initTag.putBoolean("NoGravity", true);
            applyNbt(bd, initTag);
        }

        Entity raw = level.getEntity(displayUUID);
        if (!(raw instanceof Display.BlockDisplay bd)) return;

        // Keep co-located with the black hole
        bd.setPos(center.x, center.y, center.z);

        // Build rotation quaternion
        float yAngle = (float) Math.toRadians(age * 3.0);
        float xAngle = (float) Math.toRadians(age * 1.7);
        Quaternionf rot = new Quaternionf().rotateY(yAngle).rotateX(xAngle);

        // Scale cube so its visual diameter matches the event-horizon radius
        float scale = size * 2.0f;
        float half  = scale * 0.5f;

        // Build transformation compound (same format as /data command)
        CompoundTag transformTag = new CompoundTag();
        transformTag.put("translation",   floatList(-half, -half, -half));
        transformTag.put("left_rotation", floatList(rot.x(), rot.y(), rot.z(), rot.w()));
        transformTag.put("scale",         floatList(scale, scale, scale));
        transformTag.put("right_rotation",floatList(0f, 0f, 0f, 1f));

        CompoundTag updateTag = new CompoundTag();
        updateTag.put("transformation",    transformTag);
        updateTag.putInt("interpolation_duration", 2);
        updateTag.putInt("start_interpolation",    0);
        updateTag.putBoolean("NoGravity", true);

        applyNbt(bd, updateTag);
    }

    /** Builds a ListTag of FloatTags from varargs. */
    private static ListTag floatList(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    // ── Block destruction ────────────────────────────────────────────────────

    private void destroyBlocks(ServerLevel level, float size) {
        int innerCount = Math.max(4, (int) (size * size * 3));
        destroyRandom(level, this.blockPosition(), 0,      size * 3.0,               innerCount);

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

            BlockPos pos   = center.offset(dx, dy, dz);
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
        // Thin PORTAL accretion disk — compact rotating ring
        int diskCount = (int) (size * 20);
        for (int i = 0; i < diskCount; i++) {
            double a = age * 0.05 + i * (2 * Math.PI / diskCount);
            double r = size * (1.2 + RNG.nextDouble() * 1.8);
            double px = c.x + Math.cos(a) * r;
            double py = c.y + (RNG.nextDouble() - 0.5) * size * 0.15;
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
                c.x, c.y, c.z, Math.max(1, (int) (size * 3)), 0.0, 0.0, 0.0, 0.02);
    }

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
        if (tag.hasUUID("BHDisplay")) displayUUID = tag.getUUID("BHDisplay");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("BHSize", getHoleSize());
        tag.putInt("BHAge", age);
        if (displayUUID != null) tag.putUUID("BHDisplay", displayUUID);
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
