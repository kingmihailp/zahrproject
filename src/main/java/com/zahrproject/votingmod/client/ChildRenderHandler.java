package com.zahrproject.votingmod.client;

import com.zahrproject.votingmod.events.ChildEventManager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only render handler.
 * Scales the player model to 50% when the player is in "child" state,
 * so other players see you as a small character.
 * Registered on the client side only in {@link com.zahrproject.votingmod.VotingMod#clientSetup}.
 */
@OnlyIn(Dist.CLIENT)
public class ChildRenderHandler {

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!ChildEventManager.isChild(player.getUUID())) return;
        // Scale the pose stack — pivot is at the entity's feet, so the model
        // shrinks toward the ground (correct position, no floating).
        event.getPoseStack().scale(0.5f, 0.5f, 0.5f);
    }
}
