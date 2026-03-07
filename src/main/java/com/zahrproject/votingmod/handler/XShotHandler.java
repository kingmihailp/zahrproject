package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Handles the X-выстрел (X-Shot) crossbow enchantment:
 *
 *  • Doubles crossbow charge time by suppressing vanilla onUseTick()
 *    between the normal finish and the doubled finish on both sides.
 *  • On fire: consumes 10 arrows/fireworks of the stored type,
 *    fires 10 projectiles with slight spread, hurts the crossbow
 *    by 10 durability points. If fewer than 10 projectiles are
 *    available, the crossbow stays loaded without firing.
 */
public class XShotHandler {

    private static final int   SHOT_COUNT    = 10;
    private static final float SPREAD_DEGREES = 10.0f;  // ±10° per axis
    private static final float CROSSBOW_POWER = 3.15f;
    private static final Random RANDOM = new Random();

    private static boolean hasXShot(ItemStack stack) {
        return EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.XSHOT.get(), stack) > 0;
    }

    // ── Double charge time (both sides) ──────────────────────────────────────

    /**
     * Cancel vanilla onUseTick() for ticks [chargeDuration, 2·chargeDuration),
     * so the crossbow only loads after twice the normal charge time.
     * Running on both physical sides keeps client and server in sync.
     */
    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack stack = event.getItem();
        if (!(stack.getItem() instanceof CrossbowItem)) return;
        if (CrossbowItem.isCharged(stack)) return;
        if (!hasXShot(stack)) return;

        int chargeDuration = CrossbowItem.getChargeDuration(stack);
        int elapsed = stack.getUseDuration() - event.getDuration();
        if (elapsed >= chargeDuration && elapsed < chargeDuration * 2) {
            event.setCanceled(true);
        }
    }

    // ── X-shot firing ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack crossbow = event.getItemStack();
        if (!(crossbow.getItem() instanceof CrossbowItem)) return;
        if (!CrossbowItem.isCharged(crossbow)) return;
        if (!hasXShot(crossbow)) return;

        List<ItemStack> charged = getChargedProjectiles(crossbow);
        if (charged.isEmpty()) return;
        ItemStack storedAmmo = charged.get(0);

        // Cancel vanilla on both sides to prevent client-side prediction
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (event.getLevel().isClientSide()) return;
        if (!(player instanceof ServerPlayer sp)) return;

        Level level = sp.level();
        InteractionHand hand = event.getHand();
        boolean isCreative = sp.getAbilities().instabuild;
        boolean isFirework = storedAmmo.is(Items.FIREWORK_ROCKET);

        // Need SHOT_COUNT - 1 extra in inventory (1 already stored in crossbow)
        if (!isCreative && countAmmoInInventory(sp, storedAmmo) < SHOT_COUNT - 1) {
            // Not enough — leave crossbow charged, fire nothing
            return;
        }

        // Consume the extra ammo from inventory / offhand
        if (!isCreative) {
            consumeAmmo(sp, storedAmmo, SHOT_COUNT - 1);
        }

        // Fire SHOT_COUNT projectiles with random spread
        Vec3 look = sp.getLookAngle();
        Vec3 up   = sp.getUpVector(1.0f);

        for (int i = 0; i < SHOT_COUNT; i++) {
            float hSpread = (RANDOM.nextFloat() - 0.5f) * SPREAD_DEGREES * 2;
            float vSpread = (RANDOM.nextFloat() - 0.5f) * SPREAD_DEGREES * 2;
            Vec3 dir = rotateDirection(look, up, hSpread, vSpread);

            if (isFirework) {
                FireworkRocketEntity fw = new FireworkRocketEntity(
                        level, storedAmmo.copy(), sp,
                        sp.getX(), sp.getEyeY() - 0.15, sp.getZ(), true);
                fw.shoot(dir.x, dir.y, dir.z, CROSSBOW_POWER, 1.0f);
                level.addFreshEntity(fw);
                crossbow.hurtAndBreak(3, sp, p -> p.broadcastBreakEvent(hand));
            } else {
                ArrowItem arrowItem = storedAmmo.getItem() instanceof ArrowItem ai
                        ? ai : (ArrowItem) Items.ARROW;
                AbstractArrow arrow = arrowItem.createArrow(level, storedAmmo.copy(), sp);
                arrow.setPos(sp.getX(), sp.getEyeY() - 0.1, sp.getZ());
                arrow.setCritArrow(true);
                arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
                arrow.setShotFromCrossbow(true);
                int piercing = EnchantmentHelper.getTagEnchantmentLevel(
                        Enchantments.PIERCING, crossbow);
                if (piercing > 0) arrow.setPierceLevel((byte) piercing);
                arrow.pickup = isCreative
                        ? AbstractArrow.Pickup.CREATIVE_ONLY
                        : AbstractArrow.Pickup.DISALLOWED;
                arrow.shoot(dir.x, dir.y, dir.z, CROSSBOW_POWER, 1.0f);
                level.addFreshEntity(arrow);
                crossbow.hurtAndBreak(1, sp, p -> p.broadcastBreakEvent(hand));
            }
        }

        // Uncharge crossbow (clear stored projectile + Charged flag)
        crossbow.getOrCreateTag().put("ChargedProjectiles", new ListTag());
        CrossbowItem.setCharged(crossbow, false);

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS,
                1.0f, 0.8f + RANDOM.nextFloat() * 0.4f);
    }

    // ── Mob X-Shot: extra projectiles when a mob fires an X-Shot crossbow ────

    /**
     * Tag written onto every projectile spawned by our extra-shot logic so that
     * re-entrant EntityJoinLevelEvent calls from those entities are skipped.
     */
    private static final String XSHOT_EXTRA_TAG = "votingmod_xshot_extra";

    /**
     * When any non-player mob fires a crossbow that carries X-Shot, spawn
     * {@code SHOT_COUNT - 1} additional projectiles identical in kind but with
     * random spread, mirroring what {@link #onRightClick} does for players.
     *
     * Detection strategy:
     *  • For {@link AbstractArrow}: {@code arrow.getOwner()} returns the shooter directly.
     *  • For {@link FireworkRocketEntity}: {@code attachedToEntity} is private with no
     *    public getter, so we search for a living entity with an X-Shot crossbow within
     *    2 blocks of the spawn point (the firework is spawned at the shooter's position).
     */
    @SubscribeEvent
    public static void onMobCrossbowProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Entity entity = event.getEntity();

        // Skip projectiles we spawned ourselves (prevents infinite recursion)
        if (entity.getPersistentData().getBoolean(XSHOT_EXTRA_TAG)) return;

        // Only care about arrows and fireworks
        if (!(entity instanceof AbstractArrow) && !(entity instanceof FireworkRocketEntity)) return;

        LivingEntity shooter = findMobXShotShooter(entity, level);
        if (shooter == null) return;

        Vec3 velocity = entity.getDeltaMovement();
        double speed  = velocity.length();
        if (speed < 0.01) return;
        Vec3 dir = velocity.normalize();
        // Choose an "up" vector not collinear with the shot direction
        Vec3 up = Math.abs(dir.dot(new Vec3(0, 1, 0))) > 0.99
                ? new Vec3(1, 0, 0)
                : new Vec3(0, 1, 0);

        // Read firework item from original entity via public NBT serialisation
        ItemStack fireworkItem = ItemStack.EMPTY;
        if (entity instanceof FireworkRocketEntity fw) {
            CompoundTag fwNbt = new CompoundTag();
            fw.saveWithoutId(fwNbt);
            fireworkItem = fwNbt.contains("FireworksItem")
                    ? ItemStack.of(fwNbt.getCompound("FireworksItem"))
                    : new ItemStack(Items.FIREWORK_ROCKET);
        }

        for (int i = 0; i < SHOT_COUNT - 1; i++) {
            float hSpread = (RANDOM.nextFloat() - 0.5f) * SPREAD_DEGREES * 2;
            float vSpread = (RANDOM.nextFloat() - 0.5f) * SPREAD_DEGREES * 2;
            Vec3 spreadVel = rotateDirection(dir, up, hSpread, vSpread).scale(speed);

            if (entity instanceof AbstractArrow) {
                Arrow extra = new Arrow(level, shooter);
                extra.setPos(entity.getX(), entity.getY(), entity.getZ());
                extra.setDeltaMovement(spreadVel);
                extra.setCritArrow(true);
                extra.setShotFromCrossbow(true);
                extra.setSoundEvent(SoundEvents.CROSSBOW_HIT);
                extra.pickup = AbstractArrow.Pickup.DISALLOWED;
                extra.getPersistentData().putBoolean(XSHOT_EXTRA_TAG, true);
                level.addFreshEntity(extra);
            } else {
                FireworkRocketEntity extra = new FireworkRocketEntity(
                        level, fireworkItem.copy(), shooter,
                        entity.getX(), entity.getY(), entity.getZ(), true);
                extra.setDeltaMovement(spreadVel);
                extra.getPersistentData().putBoolean(XSHOT_EXTRA_TAG, true);
                level.addFreshEntity(extra);
            }
        }
    }

    /**
     * Returns the non-player {@link LivingEntity} that fired this projectile
     * via an X-Shot crossbow, or {@code null} if this projectile doesn't
     * qualify (fired by a player, or shooter has no X-Shot).
     */
    @Nullable
    private static LivingEntity findMobXShotShooter(Entity entity, ServerLevel level) {
        if (entity instanceof AbstractArrow arrow) {
            Entity owner = arrow.getOwner();
            // Players are handled by onRightClick — skip them here
            if (owner instanceof Player || !(owner instanceof LivingEntity le)) return null;
            return hasXShot(le.getMainHandItem()) ? le : null;
        }
        if (entity instanceof FireworkRocketEntity) {
            // Firework has no public owner getter; find the closest non-player
            // living entity with an X-Shot crossbow within 2 blocks of spawn point.
            AABB box = new AABB(
                    entity.getX() - 1.5, entity.getY() - 2.5, entity.getZ() - 1.5,
                    entity.getX() + 1.5, entity.getY() + 2.5, entity.getZ() + 1.5);
            return level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> !(e instanceof Player) && hasXShot(e.getMainHandItem()))
                    .stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(entity)))
                    .orElse(null);
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Reads ChargedProjectiles NBT directly (CrossbowItem.getChargedProjectiles is private). */
    private static List<ItemStack> getChargedProjectiles(ItemStack crossbow) {
        List<ItemStack> list = new java.util.ArrayList<>();
        CompoundTag tag = crossbow.getTag();
        if (tag != null && tag.contains("ChargedProjectiles", 9)) {
            ListTag listTag = tag.getList("ChargedProjectiles", 10);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(ItemStack.of(listTag.getCompound(i)));
            }
        }
        return list;
    }

    /** Count arrows of matching item type in offhand + all inventory slots. */
    private static int countAmmoInInventory(ServerPlayer player, ItemStack ammo) {
        Item type = ammo.getItem();
        int count = 0;
        if (player.getOffhandItem().getItem() == type)
            count += player.getOffhandItem().getCount();
        for (ItemStack slot : player.getInventory().items)
            if (slot.getItem() == type) count += slot.getCount();
        return count;
    }

    /** Consume {@code amount} arrows of matching item type from offhand then inventory. */
    private static void consumeAmmo(ServerPlayer player, ItemStack ammo, int amount) {
        Item type = ammo.getItem();
        int remaining = amount;

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() == type && remaining > 0) {
            int take = Math.min(remaining, offhand.getCount());
            offhand.shrink(take);
            remaining -= take;
        }
        for (ItemStack slot : player.getInventory().items) {
            if (slot.getItem() == type && remaining > 0) {
                int take = Math.min(remaining, slot.getCount());
                slot.shrink(take);
                remaining -= take;
            }
        }
    }

    /**
     * Rotate {@code dir} by {@code hDeg} degrees around {@code up},
     * then by {@code vDeg} degrees around the right axis.
     */
    private static Vec3 rotateDirection(Vec3 dir, Vec3 up, float hDeg, float vDeg) {
        float hRad = (float) Math.toRadians(hDeg);
        float vRad = (float) Math.toRadians(vDeg);

        Quaternionf hRot = new Quaternionf()
                .setAngleAxis(hRad, (float) up.x, (float) up.y, (float) up.z);
        Vector3f v = dir.toVector3f().rotate(hRot);

        Vec3 right = dir.cross(up).normalize();
        Quaternionf vRot = new Quaternionf()
                .setAngleAxis(vRad, (float) right.x, (float) right.y, (float) right.z);
        v = v.rotate(vRot);

        return new Vec3(v.x, v.y, v.z);
    }
}
