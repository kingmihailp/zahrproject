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

/**
 * Client-side renderer for the Skateboard enchantment.
 *
 * While skating (shield with Skateboard in offhand + sprinting):
 *   1. The first-person offhand render is cancelled so the shield
 *      disappears from the player's hand.
 *   2. The shield is re-drawn flat at the player's feet, aligned to
 *      the body yaw, giving a skateboard appearance.
 */
@OnlyIn(Dist.CLIENT)
public class SkateboardRenderHandler {

    /**
     * Cancels the first-person offhand item render while skating so
     * the shield is not visible in the player's hand.
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.OFF_HAND) return;
        Player local = Minecraft.getInstance().player;
        if (local != null && isSkating(local)) {
            event.setCanceled(true);
        }
    }

    /**
     * Draws the shield flat on the ground at the player's feet.
     * Fires before the player model so the shield renders underneath.
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!isSkating(player)) return;

        ItemStack shield = player.getOffhandItem();
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();

        // Rotate to match the player's body direction.
        // LivingEntityRenderer.setupRotations uses (180 - bodyYRot) around Y.
        float bodyYRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYRot));

        // Lift slightly above the ground so the shield doesn't clip into blocks.
        poseStack.translate(0.0, 0.05, 0.0);

        // Lay flat (90° around X turns the item face-down like a skateboard deck).
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

    private static boolean isSkating(Player player) {
        ItemStack offhand = player.getOffhandItem();
        return player.isSprinting()
                && offhand.getItem() == Items.SHIELD
                && EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.SKATEBOARD.get(), offhand) > 0;
    }
}
