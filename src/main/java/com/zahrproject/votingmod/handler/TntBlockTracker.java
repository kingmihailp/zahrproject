package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.PrimedBlockPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Это точно торттил?" event:
 *
 *   For 8 minutes after activation, right-clicking any block starts a 4-second
 *   fuse on it.  While the fuse runs, all clients see the block blinking white
 *   (handled by {@link com.zahrproject.votingmod.client.PrimedBlockRenderer}).
 *   After 4 seconds the server removes the block and creates a TNT explosion
 *   at its position.
 *
 *   The 8-minute event timer persists across server restarts and player
 *   reconnects via {@link TntBlockData} (SavedData in the overworld storage).
 */
public class TntBlockTracker {

    public static final String TIMER_NAME  = "Это точно торттил?";
    public static final long   DURATION_MS = 8L * 60 * 1000; // 8 minutes

    /** Fuse duration for each individual ignited block (ms). */
    private static final long FUSE_MS = 4_000L;

    private static volatile long expiryMs   = 0;
    private static volatile long durationMs = 0;

    /** Positions currently mid-fuse — prevents double-igniting the same block. */
    private static final ConcurrentHashMap<BlockPos, Boolean> PRIMED_POSITIONS =
            new ConcurrentHashMap<>();

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-TntBlockRevert");
                t.setDaemon(true);
                return t;
            });

    private static final ScheduledExecutorService BLOCK_SCHEDULER =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "VotingMod-BlockFuse");
                t.setDaemon(true);
                return t;
            });

    public static boolean isActive() {
        return System.currentTimeMillis() < expiryMs;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void activate(MinecraftServer server) {
        durationMs = DURATION_MS;
        expiryMs   = System.currentTimeMillis() + DURATION_MS;

        TntBlockData.get(server).setExpiry(expiryMs, durationMs);

        EventTimerPacket pkt = new EventTimerPacket(TIMER_NAME, DURATION_MS, DURATION_MS);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);

        scheduleRevert(DURATION_MS);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    /**
     * Right-clicking a block while the event is active starts a 4-second fuse:
     *  - the block stays in place with its own texture
     *  - all clients receive a {@link PrimedBlockPacket} and show a blinking overlay
     *  - after the fuse the server blows up the block
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isActive()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos     pos   = event.getPos();
        ServerLevel  level = player.serverLevel();
        BlockState   state = level.getBlockState(pos);

        if (state.isAir()) return;
        if (state.is(Blocks.BEDROCK)) return;
        if (state.getBlock() instanceof LiquidBlock) return;

        // Prevent double-igniting the same block
        if (PRIMED_POSITIONS.putIfAbsent(pos, Boolean.TRUE) != null) return;

        event.setCanceled(true);

        // Tell all clients to start the blinking overlay
        PrimedBlockPacket pkt = new PrimedBlockPacket(pos, FUSE_MS);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);

        // Capture dimension key so we can retrieve the level after the delay
        ResourceKey<Level> dimension = level.dimension();

        BLOCK_SCHEDULER.schedule(() -> {
            PRIMED_POSITIONS.remove(pos);
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                ServerLevel lv = srv.getLevel(dimension);
                if (lv == null) return;
                lv.removeBlock(pos, false);
                lv.explode(null,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        4.0f, Level.ExplosionInteraction.TNT);
            });
        }, FUSE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * On player login: restore the HUD timer from world-saved data if the
     * effect is still active (handles server restarts and reconnects).
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        if (!isActive()) {
            TntBlockData data      = TntBlockData.get(server);
            long savedExpiry       = data.getExpiryMs();
            long savedDuration     = data.getDurationMs();
            long remaining         = savedExpiry - System.currentTimeMillis();

            if (remaining <= 0) {
                if (savedExpiry != 0) data.clear();
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new EventTimerPacket(TIMER_NAME, 0, 0));
                return;
            }

            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            scheduleRevert(remaining);
        }

        long remaining = expiryMs - System.currentTimeMillis();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EventTimerPacket(TIMER_NAME, remaining, durationMs));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            expiryMs = 0;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;

            TntBlockData.get(srv).clear();

            srv.execute(() -> {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
