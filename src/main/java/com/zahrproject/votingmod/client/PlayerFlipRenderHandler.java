package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side handler for the "Голова вниз" voting event.
 *
 * When {@code modelsFlipped} is true, every player model is rendered
 * upside-down by:
 *   1. Translating the pose stack up by the player's bounding-box height
 *      (so the rotation pivot is at the top of the model), then
 *   2. Rotating 180° around the Z axis, which maps Y → −Y, effectively
 *      swapping the head and feet positions in world space.
 *
 * The transform is applied inside the renderer's own pushPose / popPose
 * wrapper, so it is automatically cleaned up after each player is drawn.
 */
@OnlyIn(Dist.CLIENT)
public class PlayerFlipRenderHandler {

    private static boolean modelsFlipped = false;

    /** Called by {@link com.zahrproject.votingmod.network.FlipModelPacket} on the client thread. */
    public static void setFlipped(boolean value) {
        modelsFlipped = value;
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!modelsFlipped) return;
        float height = event.getEntity().getBbHeight();
        PoseStack poseStack = event.getPoseStack();
        poseStack.translate(0.0, height, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
    }
}
