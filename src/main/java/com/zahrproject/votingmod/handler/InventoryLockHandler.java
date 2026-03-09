package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Нехватка места" event:
 *
 *   ALL items from the main inventory (main slots 9-35) are dropped.
 *   Those slots are then filled with special barrier items that cannot
 *   be removed or moved — effectively locking the inventory grid.
 *   The hotbar (slots 0-8) remains usable.
 *
 *   While active, opening any external container (chest, workbench, etc.)
 *   is immediately cancelled to prevent slot-swap exploits.
 *
 *   After the timer the barrier items are cleared and the original items
 *   (which were dropped at activation) are NOT auto-restored, since they
 *   were physically dropped into the world.
 *
 *   Items survive server restarts and player reconnects via NBT storage.
 */
public class InventoryLockHandler {

    public static final String TIMER_NAME = "Нехватка места";

    /** NBT tag placed on each barrier ItemStack to identify it as ours. */
    private static final String NBT_BARRIER_TAG = "votingmod_locked_slot";

    private static final String NBT_EXPIRY   = "votingmod_invlock_expiry";
    private static final String NBT_DURATION = "votingmod_invlock_duration";
    /** NBT key saved on the player to know barrier slots were applied. */
    private static final String NBT_BARRIERS_APPLIED = "votingmod_invlock_barriers";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-InvLockRevert");
                t.setDaemon(true);
                return t;
            });

    public static volatile long expiryMs   = 0;
    public static volatile long durationMs = 0;

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server, long duration) {
        durationMs = duration;
        expiryMs   = System.currentTimeMillis() + duration;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            dropAndLockInventory(p);
            p.getPersistentData().putLong(NBT_EXPIRY,   expiryMs);
            p.getPersistentData().putLong(NBT_DURATION, durationMs);
        }

        EventTimerPacket pkt = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);

        scheduleRevert(duration);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    /**
     * Cancel pickup of any ground item if there is no free non-barrier hotbar slot.
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack incoming = event.getItem().getItem();
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) return;
            if (ItemStack.isSameItemSameTags(slot, incoming)
                    && slot.getCount() < slot.getMaxStackSize()) return;
        }
        event.setCanceled(true);
    }

    /**
     * Prevent players from throwing barrier items onto the ground.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!isActive()) return;
        if (isLockedBarrier(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    /**
     * Close any external container (chest, workbench…) immediately when opened
     * to prevent players from swapping barrier items with container contents.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Schedule close on the next server tick to avoid Forge callback issues.
        MinecraftServer server = player.getServer();
        if (server != null) server.execute(player::closeContainer);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(NBT_EXPIRY)) return;

        long savedExpiry   = tag.getLong(NBT_EXPIRY);
        long savedDuration = tag.getLong(NBT_DURATION);
        long remaining     = savedExpiry - System.currentTimeMillis();

        if (remaining <= 0) {
            // Event expired while offline — clear any leftover barriers.
            clearBarriers(player);
            tag.remove(NBT_EXPIRY);
            tag.remove(NBT_DURATION);
            tag.remove(NBT_BARRIERS_APPLIED);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        // Re-apply barriers if they weren't already set (e.g. server restart).
        if (tag.getBoolean(NBT_BARRIERS_APPLIED)) {
            // Barriers already in place from before — just restore global timer.
        } else {
            dropAndLockInventory(player);
        }

        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            scheduleRevert(remaining);
        }

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EventTimerPacket(TIMER_NAME, remaining, savedDuration));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isActive()) {
            player.getPersistentData().putLong(NBT_EXPIRY,   expiryMs);
            player.getPersistentData().putLong(NBT_DURATION, durationMs);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Drop all items from main slots 9-35 and fill them with locked barriers. */
    private static void dropAndLockInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !isLockedBarrier(stack)) {
                player.drop(stack, false);
            }
            inv.setItem(i, makeBarrier());
        }
        player.getPersistentData().putBoolean(NBT_BARRIERS_APPLIED, true);
        player.inventoryMenu.broadcastChanges();
    }

    /** Remove all locked barriers from main slots 9-35. */
    private static void clearBarriers(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            if (isLockedBarrier(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        player.getPersistentData().remove(NBT_BARRIERS_APPLIED);
        player.inventoryMenu.broadcastChanges();
    }

    private static ItemStack makeBarrier() {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.getOrCreateTag().putByte(NBT_BARRIER_TAG, (byte) 1);
        return stack;
    }

    private static boolean isLockedBarrier(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.BARRIER
                && stack.hasTag()
                && stack.getTag().contains(NBT_BARRIER_TAG);
    }

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            expiryMs = 0;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    clearBarriers(p);
                    p.getPersistentData().remove(NBT_EXPIRY);
                    p.getPersistentData().remove(NBT_DURATION);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
