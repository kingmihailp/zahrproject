package com.zahrproject.votingmod.client;

import com.zahrproject.votingmod.handler.InventoryLockHandler;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side handler for the "Нехватка места" event.
 *
 * While the inventory lock timer is active, opening the player's own
 * inventory screen (E key) is silently cancelled so the empty state
 * is never visible.  All other containers (chests, furnaces, etc.)
 * remain accessible.
 */
@OnlyIn(Dist.CLIENT)
public class InventoryLockClientHandler {

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!EventTimerHud.isTimerActive(InventoryLockHandler.TIMER_NAME)) return;
        if (event.getScreen() instanceof InventoryScreen
                || event.getScreen() instanceof CreativeModeInventoryScreen) {
            event.setCanceled(true);
        }
    }
}
