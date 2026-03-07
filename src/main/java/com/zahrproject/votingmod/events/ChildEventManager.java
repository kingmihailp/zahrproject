package com.zahrproject.votingmod.events;

import com.mojang.logging.LogUtils;
import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.SyncChildStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the "Обратно в детство" (child) state for players:
 *  - Halves their bounding box so they fit through 1-block-high gaps.
 *  - Reduces max health to 5 hearts (10 HP).
 *  - Allows them to ride chickens by right-clicking with wheat seeds.
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

    /** Total duration of the current (or last) event in ms. Used for HUD bar fraction on reconnect. */
    private static volatile long childEventDurationMs = 0;

    /** NBT key for the expiry timestamp. */
    private static final String NBT_KEY        = "votingmod_child_expiry";
    /** NBT key that marks we reduced this player's max health. */
    private static final String NBT_KEY_HEALTH = "votingmod_child_health_reduced";

    /** Voting-event name shown in timer HUD and packets. */
    private static final String TIMER_NAME = "Обратно в детство";

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

        long durationMs = durationSeconds * 1000L;
        childEventDurationMs = durationMs;

        long expiry = System.currentTimeMillis() + durationMs;
        childPlayers.addAll(activated);
        for (UUID uuid : activated) childExpiry.put(uuid, expiry);

        // Apply smaller hitbox and reduced health
        for (ServerPlayer p : players) {
            p.refreshDimensions();
            applyChildHealth(p);
        }

        syncToAll(server);

        // Start HUD timer on all clients
        EventTimerPacket timerStart = new EventTimerPacket(TIMER_NAME, durationMs, durationMs);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);

        LOGGER.info("[VotingMod] {} player(s) became children for {} seconds.",
                activated.size(), durationSeconds);

        // Schedule revert
        SCHEDULER.schedule(() -> {
            childPlayers.removeAll(activated);
            for (UUID uuid : activated) childExpiry.remove(uuid);
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                for (UUID uuid : activated) {
                    ServerPlayer p = srv.getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        p.refreshDimensions();
                        revertChildHealth(p);
                    }
                }
                syncToAll(srv);
                EventTimerPacket timerEnd = new EventTimerPacket(TIMER_NAME, 0, 0);
                for (ServerPlayer p : srv.getPlayerList().getPlayers())
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerEnd);
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

    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!childPlayers.contains(player.getUUID())) return;

        EntityDimensions orig = event.getNewSize();
        event.setNewSize(EntityDimensions.scalable(orig.width * 0.5f, orig.height * 0.5f));
        event.setNewEyeHeight(event.getNewEyeHeight() * 0.5f);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getTarget() instanceof Chicken chicken)) return;

        Player player = event.getEntity();
        if (!childPlayers.contains(player.getUUID())) return;
        if (chicken.isVehicle()) return;
        if (player.isPassenger()) return;

        player.startRiding(chicken, true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID uuid = player.getUUID();

        if (childPlayers.contains(uuid)) {
            // Same session — effect still active server-side.
            // Re-sync this client and re-apply health in case it was reset on login.
            syncToPlayer(player);
            server.execute(() -> {
                player.refreshDimensions();
                applyChildHealth(player);
            });
            // Re-send HUD timer with remaining time and original total duration.
            Long expiry = childExpiry.get(uuid);
            if (expiry != null) {
                long remaining = expiry - System.currentTimeMillis();
                if (remaining > 0) {
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new EventTimerPacket(TIMER_NAME, remaining, childEventDurationMs));
                }
            }
            return;
        }

        long savedExpiry = player.getPersistentData().getLong(NBT_KEY);
        long now = System.currentTimeMillis();

        // Always sync the child-player set so the client is up to date.
        // This handles the case where the effect expired while the player was
        // offline (the client's set was never cleared via SyncChildStatePacket).
        syncToPlayer(player);

        if (savedExpiry > now) {
            // Effect still active — restore child state after server restart.
            long remainingMs = savedExpiry - now;
            childPlayers.add(uuid);
            childExpiry.put(uuid, savedExpiry);
            server.execute(() -> {
                player.refreshDimensions();
                applyChildHealth(player);
                syncToAll(server);  // re-broadcast updated set
            });
            // Re-send HUD timer
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, remainingMs, childEventDurationMs));
            SCHEDULER.schedule(() -> {
                childPlayers.remove(uuid);
                childExpiry.remove(uuid);
                MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
                if (srv == null) return;
                srv.execute(() -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        p.refreshDimensions();
                        revertChildHealth(p);
                    }
                    syncToAll(srv);
                    ServerPlayer p2 = srv.getPlayerList().getPlayer(uuid);
                    if (p2 != null)
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p2),
                                new EventTimerPacket(TIMER_NAME, 0, 0));
                });
            }, remainingMs, TimeUnit.MILLISECONDS);
        } else if (player.getPersistentData().getBoolean(NBT_KEY_HEALTH)) {
            // Effect expired while player was offline — restore health now.
            server.execute(() -> revertChildHealth(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!childPlayers.contains(player.getUUID())) return;
        // Death-respawn resets attribute base values — reapply the reduction.
        applyChildHealth(player);
    }

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

    /** Reduces max health to 5 hearts (10 HP) and caps current HP. */
    private static void applyChildHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(10.0);
        if (player.getHealth() > 10.0f) player.setHealth(10.0f);
        player.getPersistentData().putBoolean(NBT_KEY_HEALTH, true);
    }

    /** Restores max health to 10 hearts (20 HP). */
    private static void revertChildHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(20.0);
        player.getPersistentData().remove(NBT_KEY_HEALTH);
    }

    private static void syncToAll(MinecraftServer server) {
        SyncChildStatePacket packet = new SyncChildStatePacket(new HashSet<>(childPlayers));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
        }
    }

    private static void syncToPlayer(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncChildStatePacket(new HashSet<>(childPlayers)));
    }
}
