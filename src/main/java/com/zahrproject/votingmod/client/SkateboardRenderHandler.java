package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side renderer for the Skateboard enchantment.
 *
 * In Minecraft 1.20.x LivingEntityRenderer drives the limb-swing animation
 * via entity.walkAnimation.speed() / .position(), NOT via walkDist/walkDistO.
 * We therefore use reflection to zero those private fields before the model
 * is rendered and restore them afterwards.
 */
@OnlyIn(Dist.CLIENT)
public class SkateboardRenderHandler {

    // ── WalkAnimationState reflection ─────────────────────────────────────────
    // Resolved lazily on first skating frame using the runtime type of
    // player.walkAnimation so we never have to hardcode the package name.
    private static Field  WALK_SPEED     = null;
    private static Field  WALK_SPEED_OLD = null;
    private static boolean reflectionDone = false; // attempted at least once

    /**
     * Attempt to resolve the 'speed' and 'speedOld' fields inside
     * WalkAnimationState. Tries the official Mojang-mapped name first;
     * falls back to enumerating float fields in declaration order
     * (speedOld = index 0, speed = index 1, position = index 2).
     */
    private static void initReflection(Object walkAnimInstance) {
        if (reflectionDone) return;
        reflectionDone = true;
        try {
            Class<?> cls = walkAnimInstance.getClass();

            // Primary: official Mojang mapping names (used in dev + official runtime)
            try {
                WALK_SPEED     = ObfuscationReflectionHelper.findField(cls, "speed");
                WALK_SPEED_OLD = ObfuscationReflectionHelper.findField(cls, "speedOld");
                return; // success
            } catch (Exception ignored) { }

            // Fallback: positional float-field lookup
            // WalkAnimationState declares: float speedOld, float speed, float position
            // HotSpot preserves declaration order in getDeclaredFields().
            List<Field> floats = new ArrayList<>();
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == float.class) {
                    f.setAccessible(true);
                    floats.add(f);
                }
            }
            if (floats.size() >= 2) {
                WALK_SPEED_OLD = floats.get(0); // speedOld
                WALK_SPEED     = floats.get(1); // speed
            }
        } catch (Exception ignored) { }
    }

    // ── Per-frame saved state (keyed by entity render id) ─────────────────────
    /** float[0] = saved speedOld, float[1] = saved speed */
    private static final Map<Integer, float[]>  savedAnim     = new HashMap<>();
    private static final Map<Integer, ItemStack> hiddenOffhand = new HashMap<>();

    // ── Event handlers ────────────────────────────────────────────────────────

    /** Cancel first-person offhand render while skating. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.OFF_HAND) return;
        Player local = Minecraft.getInstance().player;
        if (local != null && isSkating(local)) {
            event.setCanceled(true);
        }
    }

    /**
     * Before the player model is drawn:
     *  1. Zero walkAnimation.speed/speedOld → neutral standing pose.
     *  2. Empty offhand slot → arm renders nothing in 3rd-person.
     *  3. Draw the shield enlarged and flat at the player's feet.
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!isSkating(player)) return;

        // ── 1. Suppress walk animation ────────────────────────────────────────
        Object walkAnim = player.walkAnimation; // no import needed — typed as Object
        initReflection(walkAnim);

        float savedSpeed = 0f, savedOld = 0f;
        if (WALK_SPEED != null && WALK_SPEED_OLD != null) {
            try {
                savedOld   = (float) WALK_SPEED_OLD.get(walkAnim);
                savedSpeed = (float) WALK_SPEED.get(walkAnim);
                WALK_SPEED_OLD.set(walkAnim, 0f);
                WALK_SPEED.set(walkAnim, 0f);
            } catch (Exception ignored) { }
        }
        savedAnim.put(player.getId(), new float[]{ savedOld, savedSpeed });

        // ── 2. Hide offhand from the player arm ───────────────────────────────
        ItemStack shield = player.getInventory().offhand.get(0);
        hiddenOffhand.put(player.getId(), shield);
        player.getInventory().offhand.set(0, ItemStack.EMPTY);

        // ── 3. Render shield flat at player's feet ────────────────────────────
        PoseStack poseStack = event.getPoseStack();
        float partialTick   = event.getPartialTick();

        poseStack.pushPose();

        float bodyYRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYRot));
        poseStack.translate(0.0, 0.05, 0.0);
        poseStack.scale(2.0f, 2.0f, 2.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                shield,
                ItemDisplayContext.FIXED,
                event.getPackedLight(),
                OverlayTexture.NO_OVERLAY,
                poseStack,
                event.getMultiBufferSource(),
                player.level(),
                player.getId()
        );

        poseStack.popPose();
    }

    /** Restore walkAnimation and offhand after the player model is drawn. */
    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        int id = event.getEntity().getId();

        // Restore walk animation
        float[] anim = savedAnim.remove(id);
        if (anim != null && WALK_SPEED != null && WALK_SPEED_OLD != null) {
            try {
                Object walkAnim = event.getEntity().walkAnimation;
                WALK_SPEED_OLD.set(walkAnim, anim[0]);
                WALK_SPEED.set(walkAnim, anim[1]);
            } catch (Exception ignored) { }
        }

        // Restore offhand
        ItemStack stored = hiddenOffhand.remove(id);
        if (stored != null) {
            event.getEntity().getInventory().offhand.set(0, stored);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static boolean isSkating(Player player) {
        ItemStack offhand = player.getOffhandItem();
        return player.isSprinting()
                && offhand.getItem() == Items.SHIELD
                && EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.SKATEBOARD.get(), offhand) > 0;
    }
}
