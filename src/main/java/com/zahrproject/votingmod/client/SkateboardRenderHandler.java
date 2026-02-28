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

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side renderer for the Skateboard enchantment.
 *
 * While skating (shield with Skateboard in offhand + sprinting):
 *   1. First-person offhand render is cancelled (RenderHandEvent).
 *   2. The offhand slot is temporarily emptied before the player model
 *      is drawn, so the arm renders nothing in third-person (Pre).
 *   3. The shield is re-drawn enlarged and flat at the player's feet (Pre).
 *   4. The offhand slot is restored after the model is drawn (Post).
 */
@OnlyIn(Dist.CLIENT)
public class SkateboardRenderHandler {

    /** Stores the shield removed from the offhand slot during model rendering. */
    private static final Map<Integer, ItemStack> hiddenOffhand = new HashMap<>();

    /** Cancels the first-person offhand render while skating. */
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
     *  - Temporarily empties the offhand slot so the 3rd-person arm renders nothing.
     *  - Draws the shield enlarged and flat at the player's feet.
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!isSkating(player)) return;

        // Hide the offhand item so the player model's arm renders nothing.
        // Restored in onRenderPlayerPost.
        ItemStack shield = player.getInventory().offhand.get(0);
        hiddenOffhand.put(player.getId(), shield);
        player.getInventory().offhand.set(0, ItemStack.EMPTY);

        // Draw the shield flat at the player's feet.
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();

        // Align to the player's body yaw (same formula as LivingEntityRenderer.setupRotations).
        float bodyYRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYRot));

        // Lift slightly so the shield doesn't clip into the ground.
        poseStack.translate(0.0, 0.05, 0.0);

        // Scale up to make it look like a skateboard deck.
        poseStack.scale(2.0f, 2.0f, 2.0f);

        // Lay flat (90° tilt around X = face-down, like a deck on the ground).
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

    /** Restores the offhand slot after the player model has been fully drawn. */
    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        ItemStack stored = hiddenOffhand.remove(event.getEntity().getId());
        if (stored != null) {
            event.getEntity().getInventory().offhand.set(0, stored);
        }
    }

    private static boolean isSkating(Player player) {
        ItemStack offhand = player.getOffhandItem();
        return player.isSprinting()
                && offhand.getItem() == Items.SHIELD
                && EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.SKATEBOARD.get(), offhand) > 0;
    }
}
