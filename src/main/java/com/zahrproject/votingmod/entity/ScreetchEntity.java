package com.zahrproject.votingmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * «Скритч» — существо, которое появляется за спиной игрока в тёмном месте.
 *
 * Поведение:
 *  • Стоит неподвижно.
 *  • Каждый тик проверяет, смотрит ли целевой игрок в его сторону.
 *    Если угол между взглядом игрока и направлением на скритча < 40° AND
 *    расстояние < 8 блоков → исчезает (игрок увидел его вовремя).
 *  • Если игрок не обернулся за {@value #BITE_TICKS} тиков (5 секунд)
 *    → наносит 8 HP (4 сердца) и исчезает.
 *  • Автоматически исчезает, если игрок выходит из игры или удаляется.
 */
public class ScreetchEntity extends Entity {

    public static final int BITE_TICKS = 100; // 5 seconds

    // Synced: client needs to know the target player for rendering direction
    private static final EntityDataAccessor<Integer> DATA_ANIM_TICK =
            SynchedEntityData.defineId(ScreetchEntity.class, EntityDataSerializers.INT);

    private UUID targetPlayerUUID = null;
    private int  biteTimer        = 0;

    // ── Construction ──────────────────────────────────────────────────────────

    public ScreetchEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setTargetPlayer(UUID uuid) {
        this.targetPlayerUUID = uuid;
    }

    // ── Data sync ─────────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ANIM_TICK, 0);
    }

    /** Animation tick for client-side leg swaying etc. */
    public int getAnimTick() {
        return this.entityData.get(DATA_ANIM_TICK);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            // Client: increment animation tick
            this.entityData.set(DATA_ANIM_TICK, getAnimTick() + 1);
            return;
        }

        // Server logic ─────────────────────────────────────────────────────────

        if (targetPlayerUUID == null) { this.discard(); return; }

        ServerLevel serverLevel = (ServerLevel) this.level();
        ServerPlayer player     = serverLevel.getServer().getPlayerList().getPlayer(targetPlayerUUID);

        // Player disconnected
        if (player == null) { this.discard(); return; }

        // Too far away (> 16 blocks) — silent despawn
        if (player.distanceTo(this) > 16.0) { this.discard(); return; }

        // Check if player is looking at this entity ────────────────────────────
        Vec3 lookDir    = player.getLookAngle();
        Vec3 toScreetch = this.position().subtract(player.getEyePosition()).normalize();
        double dot      = lookDir.dot(toScreetch);
        // dot = cos(angle); cos(40°) ≈ 0.766
        if (dot > 0.766 && player.distanceTo(this) < 8.0) {
            // Player saw the screetch in time — flee (just discard)
            this.discard();
            return;
        }

        // Bite timer ───────────────────────────────────────────────────────────
        biteTimer++;
        if (biteTimer >= BITE_TICKS) {
            // Bite! 8 HP = 4 hearts
            if (player.isAlive()) {
                player.hurt(serverLevel.damageSources().magic(), 8.0f);
            }
            this.discard();
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        biteTimer = tag.getInt("BiteTimer");
        if (tag.hasUUID("TargetPlayer")) {
            targetPlayerUUID = tag.getUUID("TargetPlayer");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BiteTimer", biteTimer);
        if (targetPlayerUUID != null) tag.putUUID("TargetPlayer", targetPlayerUUID);
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public boolean isPickable()     { return false; }
    @Override public boolean isPushable()     { return false; }
    @Override public boolean isInvulnerable() { return true;  }
    @Override public boolean shouldRenderAtSqrDistance(double dist) { return dist < 64 * 64; }

    /** How long until the bite (for rendering the "warning" animation, 0..100). */
    public int getBiteTimer() { return biteTimer; }
}
