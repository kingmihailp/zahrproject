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
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
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
 *   ALL items from the main inventory (hotbar slots 0-8 + main slots 9-35,
 *   i.e. the full 36-slot {@code items} list) are removed and stored safely
 *   in the player's persistent NBT data.  Armor (36-39) and offhand (40)
 *   slots are left untouched.
 *
 *   After the timer the hidden items are restored to their original slots.
 *   If a slot was re-occupied, overflow items go to the first free slot or
 *   are dropped at the player's feet.
 *
 *   Items survive server restarts and player reconnects via NBT storage.
 */
public class InventoryLockHandler {

    public static final String TIMER_NAME = "Нехватка места";

    private static final String NBT_HIDDEN   = "votingmod_invlock_hidden";
    private static final String NBT_EXPIRY   = "votingmod_invlock_expiry";
    private static final String NBT_DURATION = "votingmod_invlock_duration";

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
            hideNonHotbarItems(p);
            p.getPersistentData().putLong(NBT_EXPIRY,   expiryMs);
            p.getPersistentData().putLong(NBT_DURATION, durationMs);
        }

        EventTimerPacket pkt = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);

        scheduleRevert(duration);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    /** Prevent players from picking up items while their inventory is hidden. */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (isActive()) {
            event.setCanceled(true);
        }
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
            // Event expired while this player was offline — give items back now.
            restoreItems(player);
            expiryMs = 0;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        // Restore global timer state after a server restart.
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
            // Refresh the expiry stamp so items are restored correctly on next login.
            player.getPersistentData().putLong(NBT_EXPIRY,   expiryMs);
            player.getPersistentData().putLong(NBT_DURATION, durationMs);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Move all items from the main inventory (hotbar 0-8 + main 9-35) into
     * the player's persistent NBT, leaving those slots empty.
     * Armor and offhand slots are not touched.
     */
    private static void hideNonHotbarItems(ServerPlayer player) {
        ListTag hidden = new ListTag();
        Inventory inv  = player.getInventory();

        // inv.items covers all 36 main slots (hotbar 0-8, main 9-35).
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = stack.save(new CompoundTag());
            entry.putInt("Slot", i);
            hidden.add(entry);
            inv.setItem(i, ItemStack.EMPTY);
        }

        player.getPersistentData().put(NBT_HIDDEN, hidden);
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Return all hidden items to their original slots, handling conflicts by
     * overflowing to spare inventory space or dropping at the player's feet.
     * Clears all event NBT keys afterwards.
     */
    private static void restoreItems(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(NBT_HIDDEN)) return;

        ListTag  hidden = tag.getList(NBT_HIDDEN, Tag.TAG_COMPOUND);
        Inventory inv   = player.getInventory();

        for (int i = 0; i < hidden.size(); i++) {
            CompoundTag entry = hidden.getCompound(i);
            int       slot  = entry.getInt("Slot");
            ItemStack stack = ItemStack.of(entry);

            if (inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, stack);
            } else {
                // Slot now occupied — try to merge/add, otherwise drop.
                if (!inv.add(stack)) {
                    player.drop(stack, false);
                }
            }
        }

        tag.remove(NBT_HIDDEN);
        tag.remove(NBT_EXPIRY);
        tag.remove(NBT_DURATION);
        player.inventoryMenu.broadcastChanges();
    }

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            expiryMs = 0;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    restoreItems(p);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
