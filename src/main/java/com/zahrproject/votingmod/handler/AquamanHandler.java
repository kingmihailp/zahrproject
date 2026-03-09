package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "аквамен" event:
 *
 *   • Players can breathe underwater (Water Breathing applied continuously).
 *   • Players suffocate on the surface — air supply is drained and drown
 *     damage is applied every 20 ticks while not submerged.
 *   • Duration: 3 minutes. Timer survives server restarts and reconnects via NBT.
 */
public class AquamanHandler {

    public static final String TIMER_NAME = "аквамен";

    private static final String NBT_EXPIRY_KEY   = "votingmod_aquaman_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_aquaman_duration";

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-AquamanRevert");
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isActive()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isUnderWater()) {
                // Underwater: keep Water Breathing active (refreshed each tick)
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false, false));
            } else {
                // On surface: remove Water Breathing, drain air, apply drown damage
                player.removeEffect(MobEffects.WATER_BREATHING);
                player.setAirSupply(0);
                if (player.tickCount % 20 == 0 && player.isAlive()) {
                    player.hurt(((ServerLevel) player.level()).damageSources().drown(), 2.0f);
                }
            }
        }
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

    private static void scheduleRevert(long delayMs) {
        SCHEDULER.schedule(() -> {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) { expiryMs = 0; return; }
            srv.execute(() -> {
                expiryMs = 0;
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    p.removeEffect(MobEffects.WATER_BREATHING);
                    p.setAirSupply(p.getMaxAirSupply());
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
