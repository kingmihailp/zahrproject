package com.zahrproject.votingmod.handler;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * General Forge event handler for future extensions.
 * Currently a placeholder; voting logic is driven by VotingManager's scheduler.
 */
public class ForgeEventHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // When a player joins, nothing special needed.
        // VotingManager already picks up online players dynamically at vote time.
    }
}
