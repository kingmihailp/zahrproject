package com.zahrproject.votingmod.handler;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

/**
 * Handles the "_jeb" voting event:
 * - Players can permanently name ANY entity (boats, minecarts, mobs, etc.) "_jeb"
 *   using a name tag, even types that don't normally accept name tags.
 * - Named entities shimmer with cycling rainbow dust particles.
 *
 * The ability is stored in {@link JebNamingData} and survives server restarts.
 * Each entity named "_jeb" gets a persistent NBT tag {@code votingmod:jeb_rainbow}
 * so the rainbow effect also survives restarts.
 */
public class JebNamingHandler {

    /** NBT key written on entities that have been given the _jeb rainbow effect. */
    public static final String JEB_TAG = "votingmod:jeb_rainbow";

    private int tick = 0;

    // ── Entity interaction ─────────────────────────────────────────────────────

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getSide().isClient()) return;

        Player player = event.getEntity();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Ability must be unlocked
        if (!JebNamingData.get(server).isEnabled()) return;

        ItemStack held = event.getItemStack();
        if (!held.is(Items.NAME_TAG)) return;
        if (!held.hasCustomHoverName()) return;

        String name = held.getHoverName().getString();
        if (!name.equals("_jeb")) return;

        Entity target = event.getTarget();

        // Apply the name and mark the entity for rainbow particles
        target.setCustomName(Component.literal("_jeb"));
        target.setCustomNameVisible(false);
        target.getPersistentData().putBoolean(JEB_TAG, true);

        // Consume one name tag (unless creative)
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        // Cancel default handling so the name tag isn't consumed again
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(false));
    }

    // ── Rainbow particle effect ────────────────────────────────────────────────

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        if (tick % 4 != 0) return; // update every 4 ticks (~5 times/second)

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Cycle hue through the rainbow based on time
        float hue = (tick % 80) / 80.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.2f);

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!entity.getPersistentData().getBoolean(JEB_TAG)) continue;
                spawnRainbowRing(level, entity, dust);
            }
        }
    }

    private static void spawnRainbowRing(ServerLevel level, Entity entity, DustParticleOptions dust) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();
        double radius = entity.getBbWidth() * 0.5 + 0.4;

        int points = 10;
        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * 2 * Math.PI;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            level.sendParticles(dust, px, y, pz, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }
}
