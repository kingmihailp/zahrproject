package com.zahrproject.votingmod.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side render handler for the "Режим большой головы" voting event.
 *
 * When {@code active} is true, the head and hat ModelPart scale is enlarged
 * before rendering and reset to 1.0 afterwards, making every player's head
 * look disproportionately huge while leaving the body at normal size.
 */
@OnlyIn(Dist.CLIENT)
public class BigHeadRenderHandler {

    private static final float HEAD_SCALE = 3.0f;

    private static boolean active = false;

    /** Called by {@link ClientPacketHandlers#handleBigHead} on the client thread. */
    public static void setActive(boolean value) {
        active = value;
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!active) return;
        PlayerModel<?> model = event.getRenderer().getModel();
        model.head.xScale = HEAD_SCALE;
        model.head.yScale = HEAD_SCALE;
        model.head.zScale = HEAD_SCALE;
        model.hat.xScale  = HEAD_SCALE;
        model.hat.yScale  = HEAD_SCALE;
        model.hat.zScale  = HEAD_SCALE;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!active) return;
        PlayerModel<?> model = event.getRenderer().getModel();
        model.head.xScale = 1.0f;
        model.head.yScale = 1.0f;
        model.head.zScale = 1.0f;
        model.hat.xScale  = 1.0f;
        model.hat.yScale  = 1.0f;
        model.hat.zScale  = 1.0f;
    }
}
