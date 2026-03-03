package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.SyncGoldenStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GoldenPlayerHandler {

    // ── Server-side state ─────────────────────────────────────────────────────

    /** UUID → absolute expiry timestamp (ms). Populated on the server only. */
    private static final Map<UUID, Long> goldenPlayers = new ConcurrentHashMap<>();

    /** NBT key used to persist the expiry timestamp in the player's data. */
    private static final String NBT_KEY = "votingmod_midas_expiry";

    /** Scheduler used to send the post-expiry sync packet to clients. */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-GoldenRevert");
                t.setDaemon(true);
                return t;
            });

    // ── Client-side state ─────────────────────────────────────────────────────

    /**
     * Set of UUIDs that the CLIENT knows are currently golden.
     * Populated by {@link SyncGoldenStatePacket} received from the server.
     * This is what {@link com.zahrproject.votingmod.client.GoldenOverlayLayer}
     * reads — it works correctly on every client, including those connecting
     * to a remote dedicated server.
     */
    private static final Set<UUID> goldenPlayersClient = ConcurrentHashMap.newKeySet();

    // ── Item transform tables ─────────────────────────────────────────────────

    /** Items that are already "golden" — never replaced. */
    private static final Set<Item> EXEMPT_ITEMS = Set.of(
            Items.GOLDEN_CARROT,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.GOLDEN_HELMET,
            Items.GOLDEN_CHESTPLATE,
            Items.GOLDEN_LEGGINGS,
            Items.GOLDEN_BOOTS,
            Items.GOLDEN_SWORD,
            Items.GOLDEN_PICKAXE,
            Items.GOLDEN_AXE,
            Items.GOLDEN_SHOVEL,
            Items.GOLDEN_HOE,
            Items.GOLD_INGOT,
            Items.GOLD_BLOCK,
            Items.GOLD_NUGGET,
            Items.RAW_GOLD,
            Items.RAW_GOLD_BLOCK,
            Items.GOLDEN_HORSE_ARMOR,
            Items.BELL
    );

    /** Special transforms: carrot → golden carrot, apple → golden apple. */
    private static final Map<Item, Item> ITEM_TRANSFORMS = Map.of(
            Items.CARROT, Items.GOLDEN_CARROT,
            Items.APPLE,  Items.GOLDEN_APPLE
    );

    /** Any non-golden armor in these slots is replaced with the golden equivalent. */
    private static final Map<EquipmentSlot, Item> ARMOR_TRANSFORMS = Map.of(
            EquipmentSlot.HEAD,  Items.GOLDEN_HELMET,
            EquipmentSlot.CHEST, Items.GOLDEN_CHESTPLATE,
            EquipmentSlot.LEGS,  Items.GOLDEN_LEGGINGS,
            EquipmentSlot.FEET,  Items.GOLDEN_BOOTS
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /** Total duration of the current (or last) Midas event in ms. */
    private static volatile long goldenEventDurationMs = 0;

    /**
     * Marks the player as golden for durationMs milliseconds and
     * immediately applies the first hand/armor transformation.
     * Call {@link #syncToAll} once after making all desired players golden.
     */
    public static void makeGolden(ServerPlayer player, long durationMs) {
        goldenPlayers.put(player.getUUID(), System.currentTimeMillis() + durationMs);
        replaceHandItem(player, InteractionHand.MAIN_HAND);
        replaceHandItem(player, InteractionHand.OFF_HAND);
        for (Map.Entry<EquipmentSlot, Item> e : ARMOR_TRANSFORMS.entrySet()) {
            replaceArmorItem(player, e.getKey(), e.getValue());
        }
    }

    /**
     * Sends the current set of golden UUIDs to every online client and
     * schedules a follow-up sync after {@code durationMs} so the overlay
     * disappears automatically when the effect expires.
     */
    public static void syncToAll(MinecraftServer server, long durationMs) {
        goldenEventDurationMs = durationMs;
        sendSync(server);
        // Start HUD timer on all clients
        EventTimerPacket timerStart = new EventTimerPacket("Прикосновение Мидаса", durationMs, durationMs);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);
        // After expiry: sync golden state and remove HUD timer
        SCHEDULER.schedule(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                sendSync(srv);
                EventTimerPacket timerEnd = new EventTimerPacket("Прикосновение Мидаса", 0, 0);
                for (ServerPlayer p : srv.getPlayerList().getPlayers())
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerEnd);
            });
        }, durationMs, TimeUnit.MILLISECONDS);
    }

    /** Sends the current golden-UUID set to a single (just-logged-in) player. */
    public static void syncToPlayer(ServerPlayer player) {
        Set<UUID> active = buildActiveSet();
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncGoldenStatePacket(active));
    }

    /**
     * Called by {@link SyncGoldenStatePacket} on the CLIENT thread.
     * Replaces the client's knowledge of who is currently golden.
     */
    public static void setGoldenPlayers(Set<UUID> uuids) {
        goldenPlayersClient.clear();
        goldenPlayersClient.addAll(uuids);
    }

    /**
     * Returns true if the given UUID belongs to a golden player.
     * Checks the server-side expiry map first; falls back to the
     * client-side set (populated via sync packet on remote clients).
     */
    public static boolean isGolden(UUID uuid) {
        Long expiry = goldenPlayers.get(uuid);
        if (expiry != null) {
            if (System.currentTimeMillis() < expiry) return true;
            goldenPlayers.remove(uuid);
        }
        return goldenPlayersClient.contains(uuid);
    }

    // ── Login / logout persistence ────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        // Always sync the golden-player set so the client overlay is up to date.
        // This handles the case where the effect expired while the player was
        // offline (the client's goldenPlayersClient set was never cleared).
        syncToPlayer(player);

        if (isGolden(uuid)) {
            // Same session — already in the server map. Re-send HUD timer.
            Long expiry = goldenPlayers.get(uuid);
            if (expiry != null) {
                long remaining = expiry - System.currentTimeMillis();
                if (remaining > 0) {
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new EventTimerPacket("Прикосновение Мидаса", remaining, goldenEventDurationMs));
                }
            }
            return;
        }

        // Server restart — restore from persistent player NBT.
        long savedExpiry = player.getPersistentData().getLong(NBT_KEY);
        long now = System.currentTimeMillis();
        if (savedExpiry > now) {
            goldenPlayers.put(uuid, savedExpiry);
            replaceHandItem(player, InteractionHand.MAIN_HAND);
            replaceHandItem(player, InteractionHand.OFF_HAND);
            for (Map.Entry<EquipmentSlot, Item> e : ARMOR_TRANSFORMS.entrySet()) {
                replaceArmorItem(player, e.getKey(), e.getValue());
            }
            syncToPlayer(player);
            long remainingMs = savedExpiry - now;
            // Re-send HUD timer with original total duration for correct bar fraction
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket("Прикосновение Мидаса", remainingMs, goldenEventDurationMs));
            SCHEDULER.schedule(() -> {
                MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
                if (srv == null) return;
                srv.execute(() -> {
                    sendSync(srv);
                    EventTimerPacket timerEnd = new EventTimerPacket("Прикосновение Мидаса", 0, 0);
                    for (ServerPlayer p : srv.getPlayerList().getPlayers())
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerEnd);
                });
            }, remainingMs, TimeUnit.MILLISECONDS);
        }
        // else: effect expired while offline → syncToPlayer() already sent the
        // correct (empty/partial) set above, clearing the golden overlay.
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        Long expiry = goldenPlayers.get(uuid);
        if (expiry != null && expiry > System.currentTimeMillis()) {
            player.getPersistentData().putLong(NBT_KEY, expiry);
        } else {
            player.getPersistentData().remove(NBT_KEY);
        }
    }

    // ── Hand item replacement (continuous) + gold trail ───────────────────────

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!isGolden(player.getUUID())) return;

        replaceHandItem(player, InteractionHand.MAIN_HAND);
        replaceHandItem(player, InteractionHand.OFF_HAND);
        for (Map.Entry<EquipmentSlot, Item> e : ARMOR_TRANSFORMS.entrySet()) {
            replaceArmorItem(player, e.getKey(), e.getValue());
        }

        if (player.tickCount % 4 == 0) {
            BlockPos below = player.blockPosition().below();
            ServerLevel level = player.serverLevel();
            BlockState state = level.getBlockState(below);
            if (!state.isAir() && !state.is(Blocks.WATER) && !state.is(Blocks.GOLD_BLOCK)) {
                level.setBlock(below, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    // ── Block break → replace with gold ──────────────────────────────────────

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!isGolden(player.getUUID())) return;
        if (event.getState().is(Blocks.GOLD_BLOCK)) return; // allow breaking gold blocks normally
        event.setCanceled(true);
        player.serverLevel().setBlock(event.getPos(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
    }

    // ── Block use → replace with gold ────────────────────────────────────────

    @SubscribeEvent
    public void onBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isGolden(player.getUUID())) return;
        event.setCanceled(true);
        player.serverLevel().setBlock(event.getPos(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void replaceHandItem(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || EXEMPT_ITEMS.contains(stack.getItem())) return;
        Item result = ITEM_TRANSFORMS.getOrDefault(stack.getItem(), Items.GOLD_INGOT);
        player.setItemInHand(hand, new ItemStack(result, stack.getCount()));
    }

    private static void replaceArmorItem(ServerPlayer player, EquipmentSlot slot, Item golden) {
        ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty() || stack.getItem() == golden) return;
        player.setItemSlot(slot, new ItemStack(golden));
    }

    private static Set<UUID> buildActiveSet() {
        long now = System.currentTimeMillis();
        Set<UUID> active = new HashSet<>();
        for (Map.Entry<UUID, Long> e : goldenPlayers.entrySet()) {
            if (e.getValue() > now) active.add(e.getKey());
        }
        return active;
    }

    private static void sendSync(MinecraftServer server) {
        SyncGoldenStatePacket pkt = new SyncGoldenStatePacket(buildActiveSet());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }
}
