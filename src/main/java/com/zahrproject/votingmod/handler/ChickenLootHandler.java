package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Куриное богатство" event:
 *
 *   • All chickens in the world lay a random item from the Forge item registry
 *     instead of an egg for 10 minutes.
 *   • Eggs (Items.EGG) are explicitly excluded from the random pool.
 *   • Duration: 10 minutes. Timer survives server restarts and reconnects via NBT.
 */
public class ChickenLootHandler {

    public static final String TIMER_NAME    = "Куриное богатство";
    public static final long   DURATION_MS   = 10 * 60 * 1000L; // 10 minutes

    private static final String NBT_EXPIRY_KEY   = "votingmod_chicken_loot_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_chicken_loot_duration";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-ChickenLootRevert");
                t.setDaemon(true);
                return t;
            });

    public static volatile long expiryMs   = 0;
    public static volatile long durationMs = 0;

    /** All items in ForgeRegistries except Items.EGG — built once on first activation. */
    private static volatile List<Item> itemPool = null;

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server, long duration) {
        durationMs = duration;
        expiryMs   = System.currentTimeMillis() + duration;

        buildItemPool();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CompoundTag tag = p.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        }

        EventTimerPacket timerPacket = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerPacket);

        scheduleRevert(duration);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    /**
     * Intercepts eggs laid by chickens and replaces them with a random item.
     *
     * Detection logic: an ItemEntity with Items.EGG, no thrower UUID (player-thrown
     * eggs always have a thrower), spawned within 1.5 blocks of a chicken.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!isActive()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (itemEntity.getItem().getItem() != Items.EGG) return;
        // Player-thrown/dropped eggs have a thrower UUID; chicken-laid eggs do not
        CompoundTag throwerCheck = new CompoundTag();
        itemEntity.saveWithoutId(throwerCheck);
        if (throwerCheck.hasUUID("Thrower")) return;

        // Confirm a chicken is nearby (eggs spawn at the chicken's position)
        var pos      = itemEntity.position();
        var chickens = event.getLevel().getEntitiesOfClass(
                Chicken.class,
                new AABB(pos.x - 1.5, pos.y - 1.5, pos.z - 1.5,
                         pos.x + 1.5, pos.y + 1.5, pos.z + 1.5)
        );
        if (chickens.isEmpty()) return;

        // Cancel the egg entity and schedule a replacement on the next server tick
        // to avoid modifying the entity list while it is being iterated.
        event.setCanceled(true);
        Item randomItem = getRandomItem();
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return;
        srv.execute(() -> {
            ItemEntity replacement = new ItemEntity(
                    event.getLevel(), pos.x, pos.y, pos.z,
                    new ItemStack(randomItem, 1)
            );
            replacement.setDefaultPickUpDelay();
            event.getLevel().addFreshEntity(replacement);
        });
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(NBT_EXPIRY_KEY)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        long savedExpiry   = tag.getLong(NBT_EXPIRY_KEY);
        long savedDuration = tag.getLong(NBT_DURATION_KEY);
        long remaining     = savedExpiry - System.currentTimeMillis();

        if (remaining <= 0) {
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            buildItemPool();
            scheduleRevert(remaining);
        }

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EventTimerPacket(TIMER_NAME, remaining, savedDuration));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag tag = player.getPersistentData();
        if (isActive()) {
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
        } else {
            tag.remove(NBT_EXPIRY_KEY);
            tag.remove(NBT_DURATION_KEY);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void buildItemPool() {
        if (itemPool != null) return;
        List<Item> pool = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item != Items.EGG) {
                pool.add(item);
            }
        }
        itemPool = pool;
    }

    private static Item getRandomItem() {
        List<Item> pool = itemPool;
        if (pool == null || pool.isEmpty()) return Items.DIAMOND; // fallback
        return pool.get((int) (Math.random() * pool.size()));
    }

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) { expiryMs = 0; return; }
            srv.execute(() -> {
                expiryMs = 0;
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    CompoundTag tag = p.getPersistentData();
                    tag.remove(NBT_EXPIRY_KEY);
                    tag.remove(NBT_DURATION_KEY);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
