package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.zahrproject.votingmod.mixin.FishingHookAccessor;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "Рыбацкая хитрость" event:
 *
 *   • Fishing hook bites almost instantly — handled by FishingHookMixin
 *     which shadows timeUntilLured and forces it to 1 every tick.
 *   • Fishing luck is set to an extreme value via the vanilla Luck mob effect
 *     (amplifier 29 = level 30), giving near-guaranteed treasure.
 *   • Duration: 6 minutes. Timer survives server restarts and reconnects via NBT.
 */
public class FishingTrickHandler {

    public static final String TIMER_NAME = "Рыбацкая хитрость";

    private static final String NBT_EXPIRY_KEY   = "votingmod_fishingtrick_expiry";
    private static final String NBT_DURATION_KEY = "votingmod_fishingtrick_duration";

    /** Luck amplifier to apply (amplifier 29 = Luck level 30). */
    private static final int LUCK_AMPLIFIER = 29;

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-FishingTrickRevert");
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

        int durationTicks = (int)(duration / 50);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CompoundTag tag = p.getPersistentData();
            tag.putLong(NBT_EXPIRY_KEY,   expiryMs);
            tag.putLong(NBT_DURATION_KEY, durationMs);
            p.addEffect(new MobEffectInstance(MobEffects.LUCK, durationTicks, LUCK_AMPLIFIER, false, true));
        }

        EventTimerPacket timerPacket = new EventTimerPacket(TIMER_NAME, duration, duration);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerPacket);

        scheduleRevert(duration);
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!isActive()) return;
        FishingHook hook = event.player.fishing;
        if (hook != null) {
            FishingHookAccessor accessor = (FishingHookAccessor) hook;
            if (accessor.getTimeUntilLured() > 1) {
                accessor.setTimeUntilLured(1);
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
            player.removeEffect(MobEffects.LUCK);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EventTimerPacket(TIMER_NAME, 0, 0));
            return;
        }

        if (!isActive()) {
            expiryMs   = savedExpiry;
            durationMs = savedDuration;
            scheduleRevert(remaining);
        }

        // Re-apply Luck effect in case it was lost or has wrong duration.
        player.addEffect(new MobEffectInstance(MobEffects.LUCK, (int)(remaining / 50), LUCK_AMPLIFIER, false, true));

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
                    CompoundTag tag = p.getPersistentData();
                    tag.remove(NBT_EXPIRY_KEY);
                    tag.remove(NBT_DURATION_KEY);
                    p.removeEffect(MobEffects.LUCK);
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                            new EventTimerPacket(TIMER_NAME, 0, 0));
                }
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
