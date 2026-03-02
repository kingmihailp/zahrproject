package com.zahrproject.votingmod.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side handler for the "Все вверх дном" voting event.
 * When {@code flipped} is true, rotates the camera by 180° on the Z axis,
 * making the entire screen appear upside-down.
 */
@OnlyIn(Dist.CLIENT)
public class ScreenFlipHandler {

    private static boolean flipped = false;

    /** Called by {@link com.zahrproject.votingmod.network.FlipScreenPacket} on the client thread. */
    public static void setFlipped(boolean value) {
        flipped = value;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (flipped) {
            event.setRoll(event.getRoll() + 180.0f);
        }
    }
}
