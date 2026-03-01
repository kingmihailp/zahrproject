package com.zahrproject.votingmod.events;

import com.mojang.logging.LogUtils;
import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.SyncChildStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the "child" state for players:
 *  - Halves their bounding box so they fit through 1-block-high gaps.
 *  - Allows them to ride chickens by right-clicking with wheat seeds.
 *
 * The state is synced to all clients via {@link SyncChildStatePacket} so
 * that {@link #onEntitySize} fires correctly on both sides.
 *
 * State persists across login/logout within the same server session and
 * across server restarts via {@code player.getPersistentData()}.
 */
public class ChildEventManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Set of UUIDs that are currently "children". Used on both sides. */
    private static final Set<UUID> childPlayers = ConcurrentHashMap.newKeySet();

    /** UUID → absolute expiry timestamp (ms). Server-side only. */
    private static final Map<UUID, Long> childExpiry = new ConcurrentHashMap<>();

    /** NBT key used to persist the expiry timestamp in the player's data. */
    private static final String NBT_KEY = "votingmod_child_expiry";

    /** Scheduler for the revert timer. */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-ChildRevert");
                t.setDaemon(true);
                return t;
            });

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called from the voting event lambda on the server main thread.
     * Makes all online players children for {@code durationSeconds} seconds,
     * then reverts them automatically.
     */
    public static void activate(MinecraftServer server, int durationSeconds) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Set<UUID> activated = new HashSet<>();
        for (ServerPlayer p : players) activated.add(p.getUUID());

        long expiry = System.currentTimeMillis() + durationSeconds * 1000L;
        childPlayers.addAll(activated);
        for (UUID uuid : activated) childExpiry.put(uuid, expiry);

        // Refresh bounding boxes so the smaller hitbox takes effect immediately
        for (ServerPlayer p : players) p.refreshDimensions();

        syncToAll(server);

        LOGGER.info("[VotingMod] {} player(s) became children for {} seconds.",
                activated.size(), durationSeconds);

        // Schedule revert
        SCHEDULER.schedule(() -> {
            childPlayers.removeAll(activated);
            for (UUID uuid : activated) childExpiry.remove(uuid);
            server.execute(() -> {
                for (UUID uuid : activated) {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p != null) p.refreshDimensions();
                }
                syncToAll(server);
                LOGGER.info("[VotingMod] Players reverted from child state.");
            });
        }, durationSeconds, TimeUnit.SECONDS);
    }

    /** Called on the client when a {@link SyncChildStatePacket} arrives. */
    public static void setChildPlayers(Set<UUID> uuids) {
        childPlayers.clear();
        childPlayers.addAll(uuids);
    }

    public static boolean isChild(UUID uuid) {
        return childPlayers.contains(uuid);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    /**
     * Fires on BOTH sides whenever entity dimensions are queried.
     * Halves width and height for child players.
     * Normal player: 0.6 w × 1.8 h → child: 0.3 w × 0.9 h
     * A 0.9-tall hitbox fits through 1-block-high openings (< 1.0).
     */
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!childPlayers.contains(player.getUUID())) return;

        EntityDimensions orig = event.getNewSize();
        event.setNewSize(EntityDimensions.scalable(orig.width * 0.5f, orig.height * 0.5f));
        event.setNewEyeHeight(event.getNewEyeHeight() * 0.5f);
    }

    /**
     * Fires on both sides; we only act server-side.
     * When a child player right-clicks a chicken while holding wheat seeds,
     * they mount the chicken (no saddle needed).
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getTarget() instanceof Chicken chicken)) return;

        Player player = event.getEntity();
        if (!childPlayers.contains(player.getUUID())) return;
        if (!player.getItemInHand(event.getHand()).is(Items.WHEAT_SEEDS)) return;
        if (chicken.isVehicle()) return;   // chicken already has a rider
        if (player.isPassenger()) return;  // player is already riding something

        player.startRiding(chicken, true);
        event.setCanceled(true);
    }

    /**
     * On login: re-sync child state to the joining client.
     * If the server was restarted, restore the state from the player's NBT
     * (provided the saved expiry has not yet passed).
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID uuid = player.getUUID();

        if (childPlayers.contains(uuid)) {
            // Same session — UUID still tracked; just re-sync this client.
            SyncChildStatePacket pkt = new SyncChildStatePacket(new HashSet<>(childPlayers));
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
            server.execute(player::refreshDimensions);
            return;
        }

        // Server was restarted — check persistent player data.
        long savedExpiry = player.getPersistentData().getLong(NBT_KEY);
        long now = System.currentTimeMillis();
        if (savedExpiry > now) {
            long remainingMs = savedExpiry - now;
            childPlayers.add(uuid);
            childExpiry.put(uuid, savedExpiry);
            server.execute(() -> {
                player.refreshDimensions();
                syncToAll(server);
            });
            SCHEDULER.schedule(() -> {
                childPlayers.remove(uuid);
                childExpiry.remove(uuid);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p != null) p.refreshDimensions();
                    syncToAll(server);
                });
            }, remainingMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * On logout: save remaining child-state time to the player's persistent
     * NBT so the effect survives a server restart.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        Long expiry = childExpiry.get(uuid);
        if (expiry != null && expiry > System.currentTimeMillis()) {
            player.getPersistentData().putLong(NBT_KEY, expiry);
        } else {
            player.getPersistentData().remove(NBT_KEY);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void syncToAll(MinecraftServer server) {
        SyncChildStatePacket packet = new SyncChildStatePacket(new HashSet<>(childPlayers));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
        }
    }
}
