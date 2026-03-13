package com.zahrproject.votingmod.handler;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side handler for the "_jeb" voting event.
 *
 * <p>When the ability is unlocked (stored in {@link JebNamingData}), any player
 * holding a name tag named {@code "_jeb"} can right-click <em>any</em> entity —
 * including boats, minecarts, and other non-living types that normally cannot be
 * named — to apply the name and mark it for the rainbow effect.
 *
 * <p>The actual rainbow visual (model colour cycling) is rendered entirely on the
 * client by {@link com.zahrproject.votingmod.client.JebRainbowLayer}, which reads
 * the entity's custom name that is automatically synced to all clients by the
 * base Minecraft entity data system.
 */
public class JebNamingHandler {

    /** NBT key written to entities given the _jeb rainbow treatment (kept for persistence). */
    public static final String JEB_TAG = "votingmod:jeb_rainbow";

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getSide().isClient()) return;

        Player player = event.getEntity();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Ability must have been unlocked by a voting event
        if (!JebNamingData.get(server).isEnabled()) return;

        ItemStack held = event.getItemStack();
        if (!held.is(Items.NAME_TAG)) return;
        if (!held.hasCustomHoverName()) return;
        if (!"_jeb".equals(held.getHoverName().getString())) return;

        Entity target = event.getTarget();

        // Name the entity and mark it for the rainbow effect
        target.setCustomName(Component.literal("_jeb"));
        target.setCustomNameVisible(false);
        // Also write the NBT tag so server-side code can identify jeb entities if needed
        target.getPersistentData().putBoolean(JEB_TAG, true);

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        // Cancel default handling so vanilla doesn't consume the name tag a second time
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(false));
    }
}
