package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles the M-выстрел (M-Shot) crossbow enchantment:
 *
 *  • Multiplies crossbow charge time by 5 (cancel vanilla ticks up to 5× normal).
 *  • On fire: consumes 99 arrows/fireworks of the stored type (1 already in
 *    crossbow = 100 total), fires 100 projectiles with slight spread.
 *    If fewer than 100 are available, the crossbow stays loaded without firing.
 */
public class MShotHandler {

    private static final int   SHOT_COUNT     = 100;
    private static final int   CHARGE_MULT    = 5;      // 5× normal crossbow charge time
    private static final float SPREAD_DEGREES = 10.0f;
    private static final float CROSSBOW_POWER = 3.15f;
    private static final Random RANDOM = new Random();

    private static boolean hasMShot(ItemStack stack) {
        return EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.MSHOT.get(), stack) > 0;
    }

    // ── Extended charge time ──────────────────────────────────────────────────

    /**
     * Cancel vanilla onUseTick() for ticks [chargeDuration, CHARGE_MULT × chargeDuration),
     * so the crossbow only loads after CHARGE_MULT times the normal charge time.
     * Runs on both physical sides to keep client and server in sync.
     */
    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack stack = event.getItem();
        if (!(stack.getItem() instanceof CrossbowItem)) return;
        if (CrossbowItem.isCharged(stack)) return;
        if (!hasMShot(stack)) return;

        int chargeDuration = CrossbowItem.getChargeDuration(stack);
        int elapsed = stack.getUseDuration() - event.getDuration();
        if (elapsed >= chargeDuration && elapsed < chargeDuration * CHARGE_MULT) {
            event.setCanceled(true);
        }
    }

    // ── M-shot firing ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack crossbow = event.getItemStack();
        if (!(crossbow.getItem() instanceof CrossbowItem)) return;
        if (!CrossbowItem.isCharged(crossbow)) return;
        if (!hasMShot(crossbow)) return;

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

        // Uncharge crossbow
        crossbow.getOrCreateTag().put("ChargedProjectiles", new ListTag());
        CrossbowItem.setCharged(crossbow, false);

        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS,
                1.0f, 0.8f + RANDOM.nextFloat() * 0.4f);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<ItemStack> getChargedProjectiles(ItemStack crossbow) {
        List<ItemStack> list = new ArrayList<>();
        CompoundTag tag = crossbow.getTag();
        if (tag != null && tag.contains("ChargedProjectiles", 9)) {
            ListTag listTag = tag.getList("ChargedProjectiles", 10);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(ItemStack.of(listTag.getCompound(i)));
            }
        }
        return list;
    }

    private static int countAmmoInInventory(ServerPlayer player, ItemStack ammo) {
        Item type = ammo.getItem();
        int count = 0;
        if (player.getOffhandItem().getItem() == type)
            count += player.getOffhandItem().getCount();
        for (ItemStack slot : player.getInventory().items)
            if (slot.getItem() == type) count += slot.getCount();
        return count;
    }

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
