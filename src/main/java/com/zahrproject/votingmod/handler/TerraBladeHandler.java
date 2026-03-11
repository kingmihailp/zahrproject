package com.zahrproject.votingmod.handler;

import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.TerraBladeWavePacket;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Terra Blade enchantment effects:
 *
 *   Ближний бой — удвоение урона + кольцо dust_color_transition (лайм → жёлтый)
 *   Дальняя атака — дуговая волна частиц в направлении взгляда;
 *                   при попадании наносит 8 HP (4 сердца) с игнорированием брони.
 */
public class TerraBladeHandler {

    // ── Dust color transition: lime → yellow ──────────────────────────────────
    private static final Vector3f LIME   = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final Vector3f YELLOW = new Vector3f(1.0f, 1.0f, 0.0f);
    private static final DustColorTransitionOptions DUST =
            new DustColorTransitionOptions(LIME, YELLOW, 1.5f);
    private static final DustColorTransitionOptions DUST_WAVE =
            new DustColorTransitionOptions(LIME, YELLOW, 1.2f);

    // ── Wave parameters ───────────────────────────────────────────────────────
    private static final double WAVE_SPEED      = 1.5;  // blocks per tick
    private static final int    WAVE_MAX_TICKS  = 16;   // 24 blocks max range
    private static final float  WAVE_HIT_RADIUS = 0.8f;
    private static final float  WAVE_DAMAGE     = 8.0f; // 4 hearts, armor-ignoring
    private static final int    WAVE_COOLDOWN   = 10;   // ticks between waves

    // ── State ─────────────────────────────────────────────────────────────────
    private static final List<TerraWave>            ACTIVE_WAVES  = Collections.synchronizedList(new ArrayList<>());
    private static final Map<UUID, Long>            WAVE_COOLDOWNS = new ConcurrentHashMap<>();

    private static final class TerraWave {
        double x, y, z;
        final double dx, dy, dz;
        int ticksLeft;
        final ServerPlayer owner;
        final Set<LivingEntity> hit = new HashSet<>();

        TerraWave(ServerPlayer owner, Vec3 pos, Vec3 dir) {
            this.owner    = owner;
            this.x        = pos.x;
            this.y        = pos.y;
            this.z        = pos.z;
            this.dx       = dir.x * WAVE_SPEED;
            this.dy       = dir.y * WAVE_SPEED;
            this.dz       = dir.z * WAVE_SPEED;
            this.ticksLeft = WAVE_MAX_TICKS;
        }
    }

    // ── Melee: LivingHurtEvent ────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() == null) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.TERRA_BLADE.get(), weapon) <= 0) return;

        // Double the damage
        event.setAmount(event.getAmount() * 2.0f);

        // Particle ring around the target
        LivingEntity target = event.getEntity();
        double cx = target.getX();
        double cy = target.getY() + target.getBbHeight() * 0.5;
        double cz = target.getZ();

        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2 / 16;
            level.sendParticles(DUST,
                    cx + Math.cos(angle) * 0.8, cy, cz + Math.sin(angle) * 0.8,
                    1, 0.08, 0.2, 0.08, 0.0);
        }
        level.sendParticles(DUST, cx, cy, cz, 8, 0.3, 0.4, 0.3, 0.0);
    }

    // ── Ranged: left-click in empty space (fires CLIENT-side only) ────────────

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) return;

        ItemStack weapon = player.getMainHandItem();
        if (EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.TERRA_BLADE.get(), weapon) <= 0) return;

        ModNetwork.CHANNEL.sendToServer(new TerraBladeWavePacket());
    }

    // ── Called from TerraBladeWavePacket on the server ────────────────────────

    public static void trySpawnWave(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        ItemStack weapon = player.getMainHandItem();
        if (EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.TERRA_BLADE.get(), weapon) <= 0) return;

        long now = level.getGameTime();
        Long lastTime = WAVE_COOLDOWNS.get(player.getUUID());
        if (lastTime != null && now - lastTime < WAVE_COOLDOWN) return;
        WAVE_COOLDOWNS.put(player.getUUID(), now);

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0f, 0.8f);

        Vec3 start = player.getEyePosition();
        Vec3 look  = player.getLookAngle();
        ACTIVE_WAVES.add(new TerraWave(player, start, look));
    }

    // ── Server tick: advance waves ─────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ACTIVE_WAVES.isEmpty()) return;

        synchronized (ACTIVE_WAVES) {
            Iterator<TerraWave> it = ACTIVE_WAVES.iterator();
            while (it.hasNext()) {
                TerraWave wave = it.next();

                if (!wave.owner.isAlive() || wave.ticksLeft <= 0) {
                    it.remove();
                    continue;
                }

                wave.x += wave.dx;
                wave.y += wave.dy;
                wave.z += wave.dz;
                wave.ticksLeft--;

                ServerLevel level = wave.owner.serverLevel();

                // Arc of particles around the wave centre
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI * 2 / 8;
                    level.sendParticles(DUST_WAVE,
                            wave.x + Math.cos(angle) * 0.5,
                            wave.y,
                            wave.z + Math.sin(angle) * 0.5,
                            1, 0.05, 0.1, 0.05, 0.0);
                }

                // Hit detection
                AABB box = new AABB(
                        wave.x - WAVE_HIT_RADIUS, wave.y - WAVE_HIT_RADIUS, wave.z - WAVE_HIT_RADIUS,
                        wave.x + WAVE_HIT_RADIUS, wave.y + WAVE_HIT_RADIUS, wave.z + WAVE_HIT_RADIUS);

                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != wave.owner && !wave.hit.contains(e));

                for (LivingEntity target : targets) {
                    // Armor-ignoring magic damage (4 hearts = 8 HP)
                    target.hurt(level.damageSources().magic(), WAVE_DAMAGE);
                    wave.hit.add(target);

                    // Hit burst particles
                    double tx = target.getX();
                    double ty = target.getY() + target.getBbHeight() * 0.5;
                    double tz = target.getZ();
                    level.sendParticles(DUST, tx, ty, tz, 14, 0.4, 0.5, 0.4, 0.0);
                }
            }
        }
    }
}
