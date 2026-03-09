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
 *  • Если цели нет (заспавнен через /summon) — просто стоит, не кусает.
 */
public class ScreetchEntity extends Entity {

    public static final int BITE_TICKS = 100; // 5 seconds

    private static final EntityDataAccessor<Integer> DATA_ANIM_TICK =
            SynchedEntityData.defineId(ScreetchEntity.class, EntityDataSerializers.INT);

    /** Синхронизируется на клиент, чтобы модель могла анимировать открытие пасти. */
    private static final EntityDataAccessor<Integer> DATA_BITE_TIMER =
            SynchedEntityData.defineId(ScreetchEntity.class, EntityDataSerializers.INT);

    private UUID targetPlayerUUID = null;

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
        this.entityData.define(DATA_BITE_TIMER, 0);
    }

    /** Animation tick — инкрементируется на клиенте для анимации лап. */
    public int getAnimTick() {
        return this.entityData.get(DATA_ANIM_TICK);
    }

    /** Сколько тиков прошло с появления скритча (0..BITE_TICKS). Синхронизировано. */
    public int getBiteTimer() {
        return this.entityData.get(DATA_BITE_TIMER);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            this.entityData.set(DATA_ANIM_TICK, getAnimTick() + 1);
            return;
        }

        // Заспавнен без цели (/summon или аналог) — просто стоит, не кусает
        if (targetPlayerUUID == null) return;

        ServerLevel serverLevel = (ServerLevel) this.level();
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(targetPlayerUUID);

        // Игрок вышел
        if (player == null) { this.discard(); return; }

        // Слишком далеко (> 16 блоков) — тихий деспавн
        if (player.distanceTo(this) > 16.0) { this.discard(); return; }

        // Держим дистанцию ≥ 2 блоков от игрока ──────────────────────────────
        double dx = this.getX() - player.getX();
        double dz = this.getZ() - player.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 2.0) {
            if (horizDist < 0.01) {
                // Прямо на игроке — отступаем назад от взгляда игрока
                Vec3 look = player.getLookAngle();
                dx = -look.x; dz = -look.z;
            }
            double factor = 2.0 / Math.sqrt(dx * dx + dz * dz);
            this.setPos(player.getX() + dx * factor, player.getY(), player.getZ() + dz * factor);
        }

        // Проверяем угол взгляда ───────────────────────────────────────────────
        Vec3 lookDir    = player.getLookAngle();
        Vec3 toScreetch = this.position().subtract(player.getEyePosition()).normalize();
        double dot      = lookDir.dot(toScreetch);
        // cos(40°) ≈ 0.766 — если игрок смотрит в сторону скритча менее чем на 40°
        if (dot > 0.766 && player.distanceTo(this) < 8.0) {
            this.discard();
            return;
        }

        // Таймер укуса ─────────────────────────────────────────────────────────
        int timer = getBiteTimer() + 1;
        this.entityData.set(DATA_BITE_TIMER, timer);

        if (timer >= BITE_TICKS) {
            if (player.isAlive()) {
                player.hurt(serverLevel.damageSources().magic(), 8.0f);
            }
            this.discard();
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_BITE_TIMER, tag.getInt("BiteTimer"));
        if (tag.hasUUID("TargetPlayer")) {
            targetPlayerUUID = tag.getUUID("TargetPlayer");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BiteTimer", getBiteTimer());
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
}
