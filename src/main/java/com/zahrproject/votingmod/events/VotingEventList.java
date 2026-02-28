package com.zahrproject.votingmod.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * All individual voting events.
 * Players vote YES (event happens) or NO (event is cancelled).
 */
public class VotingEventList {

    private static final Random RANDOM = new Random();

    /** Loot tables used by the random chest event. */
    private static final ResourceLocation[] CHEST_LOOT_TABLES = {
            new ResourceLocation("minecraft", "chests/simple_dungeon"),
            new ResourceLocation("minecraft", "chests/jungle_temple"),
            new ResourceLocation("minecraft", "chests/desert_pyramid"),
            new ResourceLocation("minecraft", "chests/stronghold_corridor"),
            new ResourceLocation("minecraft", "chests/stronghold_library"),
            new ResourceLocation("minecraft", "chests/village/village_weaponsmith"),
            new ResourceLocation("minecraft", "chests/village/village_toolsmith"),
            new ResourceLocation("minecraft", "chests/abandoned_mineshaft"),
            new ResourceLocation("minecraft", "chests/nether_bridge"),
            new ResourceLocation("minecraft", "chests/end_city_treasure"),
            new ResourceLocation("minecraft", "chests/igloo_chest"),
            new ResourceLocation("minecraft", "chests/woodland_mansion"),
            new ResourceLocation("minecraft", "chests/bastion_treasure"),
            new ResourceLocation("minecraft", "chests/ruined_portal"),
            new ResourceLocation("minecraft", "chests/shipwreck_treasure"),
            new ResourceLocation("minecraft", "chests/buried_treasure"),
    };

    public static List<VotingEvent> buildEventList() {
        List<VotingEvent> events = new ArrayList<>();

        // ── Mob spawns ─────────────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Призвать 5 зомби рядом с каждым игроком",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        for (int i = 0; i < 5; i++) {
                            Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
                            double ox = (RANDOM.nextDouble() - 0.5) * 6;
                            double oz = (RANDOM.nextDouble() - 0.5) * 6;
                            zombie.moveTo(pos.getX() + ox, pos.getY(), pos.getZ() + oz, 0, 0);
                            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                                    MobSpawnType.EVENT, null, null);
                            level.addFreshEntity(zombie);
                        }
                    }
                    broadcast(server, "Вокруг игроков появились зомби!");
                }
        ));

        events.add(new VotingEvent(
                "Призвать 3 криппера рядом с каждым игроком",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        for (int i = 0; i < 3; i++) {
                            Creeper creeper = new Creeper(EntityType.CREEPER, level);
                            double ox = (RANDOM.nextDouble() - 0.5) * 8;
                            double oz = (RANDOM.nextDouble() - 0.5) * 8;
                            creeper.moveTo(pos.getX() + ox, pos.getY(), pos.getZ() + oz, 0, 0);
                            creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                                    MobSpawnType.EVENT, null, null);
                            level.addFreshEntity(creeper);
                        }
                    }
                    broadcast(server, "Вокруг игроков появились крипперы!");
                }
        ));

        events.add(new VotingEvent(
                "Ударить молнией рядом с каждым игроком",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                        if (bolt != null) {
                            bolt.moveTo(Vec3.atBottomCenterOf(pos.offset(3, 0, 3)));
                            bolt.setVisualOnly(false);
                            level.addFreshEntity(bolt);
                        }
                    }
                    broadcast(server, "Молния ударила рядом с игроками!");
                }
        ));

        // ── Weather ────────────────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Установить погоду — гроза",
                server -> {
                    for (ServerLevel level : server.getAllLevels())
                        level.setWeatherParameters(0, 6000, true, true);
                    broadcast(server, "Погода изменена на грозу!");
                }
        ));

        events.add(new VotingEvent(
                "Установить погоду — ясный день",
                server -> {
                    for (ServerLevel level : server.getAllLevels())
                        level.setWeatherParameters(6000, 0, false, false);
                    broadcast(server, "Погода стала ясной!");
                }
        ));

        // ── Time ───────────────────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Установить время суток — рассвет",
                server -> {
                    for (ServerLevel level : server.getAllLevels()) level.setDayTime(0);
                    broadcast(server, "Наступил рассвет!");
                }
        ));

        events.add(new VotingEvent(
                "Установить время суток — полночь",
                server -> {
                    for (ServerLevel level : server.getAllLevels()) level.setDayTime(18000);
                    broadcast(server, "Наступила полночь!");
                }
        ));

        // ── Positive effects ───────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Дать всем игрокам скорость на 1 минуту",
                server -> {
                    applyEffect(server, MobEffects.MOVEMENT_SPEED, 1200, 1);
                    broadcast(server, "Все игроки получили ускорение!");
                }
        ));

        events.add(new VotingEvent(
                "Полностью восстановить здоровье и еду всем игрокам",
                server -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.setHealth(p.getMaxHealth());
                        p.getFoodData().setFoodLevel(20);
                    }
                    broadcast(server, "Все игроки полностью восстановлены!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам ночное зрение на 2 минуты",
                server -> {
                    applyEffect(server, MobEffects.NIGHT_VISION, 2400, 0);
                    broadcast(server, "Все игроки получили ночное зрение!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам силу II на 2 минуты",
                server -> {
                    applyEffect(server, MobEffects.DAMAGE_BOOST, 2400, 1);
                    broadcast(server, "Все игроки получили силу!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам прыгучесть IV на 1 минуту",
                server -> {
                    applyEffect(server, MobEffects.JUMP, 1200, 4);
                    broadcast(server, "Все игроки прыгают высоко!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам огнеупорность на 3 минуты",
                server -> {
                    applyEffect(server, MobEffects.FIRE_RESISTANCE, 3600, 0);
                    broadcast(server, "Все игроки получили огнеупорность!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам бодрость III на 2 минуты",
                server -> {
                    applyEffect(server, MobEffects.DIG_SPEED, 2400, 2);
                    broadcast(server, "Все игроки копают быстрее!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам регенерацию II на 30 секунд",
                server -> {
                    applyEffect(server, MobEffects.REGENERATION, 600, 1);
                    broadcast(server, "Все игроки быстро восстанавливаются!");
                }
        ));

        // ── Negative effects ───────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Дать всем игрокам замедление на 1 минуту",
                server -> {
                    applyEffect(server, MobEffects.MOVEMENT_SLOWDOWN, 1200, 2);
                    broadcast(server, "Все игроки получили замедление!");
                }
        ));

        events.add(new VotingEvent(
                "Сделать всех игроков очень голодными",
                server -> {
                    applyEffect(server, MobEffects.HUNGER, 600, 4);
                    for (ServerPlayer p : server.getPlayerList().getPlayers())
                        p.getFoodData().setFoodLevel(2);
                    broadcast(server, "Все игроки стали очень голодными!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам слепоту на 30 секунд",
                server -> {
                    applyEffect(server, MobEffects.BLINDNESS, 600, 0);
                    broadcast(server, "Все игроки ослепли!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам слабость II на 2 минуты",
                server -> {
                    applyEffect(server, MobEffects.WEAKNESS, 2400, 1);
                    broadcast(server, "Все игроки получили слабость!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам левитацию на 10 секунд",
                server -> {
                    applyEffect(server, MobEffects.LEVITATION, 200, 2);
                    broadcast(server, "Все игроки взлетели!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам усталость копания II на 2 минуты",
                server -> {
                    applyEffect(server, MobEffects.DIG_SLOWDOWN, 2400, 2);
                    broadcast(server, "Все игроки копают медленнее!");
                }
        ));

        events.add(new VotingEvent(
                "Отравить всех игроков II на 15 секунд",
                server -> {
                    applyEffect(server, MobEffects.POISON, 300, 1);
                    broadcast(server, "Все игроки отравлены!");
                }
        ));

        // ── Items ──────────────────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Дать всем игрокам 5 алмазов",
                server -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers())
                        p.getInventory().add(new ItemStack(Items.DIAMOND, 5));
                    broadcast(server, "Все игроки получили алмазы!");
                }
        ));

        events.add(new VotingEvent(
                "Дать всем игрокам 16 гнилого мяса",
                server -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers())
                        p.getInventory().add(new ItemStack(Items.ROTTEN_FLESH, 16));
                    broadcast(server, "Все игроки получили гнилое мясо!");
                }
        ));

        // ── Special: children ──────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Превратить всех игроков в детей на 1 минуту",
                server -> {
                    ChildEventManager.activate(server, 60);
                    broadcast(server, "Все игроки стали детьми! Ищите дыры в заборе!");
                }
        ));

        // ── Special: random loot chest ─────────────────────────────────────────

        events.add(new VotingEvent(
                "Поставить рядом с каждым игроком сундук со случайным лутом!",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos chestPos = findSafeSpot(level, player.blockPosition());

                        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);

                        BlockEntity be = level.getBlockEntity(chestPos);
                        if (be instanceof RandomizableContainerBlockEntity container) {
                            ResourceLocation lootTable =
                                    CHEST_LOOT_TABLES[RANDOM.nextInt(CHEST_LOOT_TABLES.length)];
                            container.setLootTable(lootTable, RANDOM.nextLong());
                        }
                    }
                    broadcast(server, "Рядом с каждым игроком появился сундук с сокровищами!");
                }
        ));

        return events;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void applyEffect(MinecraftServer server,
                                    net.minecraft.world.effect.MobEffect effect,
                                    int durationTicks, int amplifier) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Голосование] §e" + message), false);
    }

    /**
     * Finds an air block adjacent to the player that has solid ground below.
     * Falls back to one block above the player if nothing better is found.
     */
    private static BlockPos findSafeSpot(ServerLevel level, BlockPos origin) {
        int[] dx = {1, -1, 2, -2, 1, -1};
        int[] dz = {0,  0, 0,  0, 1, -1};
        for (int i = 0; i < dx.length; i++) {
            BlockPos candidate = origin.offset(dx[i], 0, dz[i]);
            if (level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()
                    && !level.getBlockState(candidate.below()).isAir()) {
                return candidate;
            }
        }
        return origin.above();
    }
}
