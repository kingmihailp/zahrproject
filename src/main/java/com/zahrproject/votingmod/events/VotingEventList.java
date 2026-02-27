package com.zahrproject.votingmod.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Contains all available voting events.
 * Add new events here to expand the pool.
 */
public class VotingEventList {

    private static final Random RANDOM = new Random();

    public static List<VotingEvent> buildEventList() {
        List<VotingEvent> events = new ArrayList<>();

        // Event 1: Zombies vs Thunderstorm
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
                    broadcastResult(server, "Вокруг игроков появились зомби!");
                },
                "Установить погоду на грозу",
                server -> {
                    for (ServerLevel level : server.getAllLevels()) {
                        level.setWeatherParameters(0, 6000, true, true);
                    }
                    broadcastResult(server, "Погода изменена на грозу!");
                }
        ));

        // Event 2: Speed vs Slowness
        events.add(new VotingEvent(
                "Дать всем игрокам скорость на 1 минуту",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
                    }
                    broadcastResult(server, "Все игроки получили ускорение!");
                },
                "Дать всем игрокам замедление на 1 минуту",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 2));
                    }
                    broadcastResult(server, "Все игроки получили замедление!");
                }
        ));

        // Event 3: Heal vs Hunger
        events.add(new VotingEvent(
                "Полностью восстановить здоровье всем игрокам",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.setHealth(player.getMaxHealth());
                        player.getFoodData().setFoodLevel(20);
                    }
                    broadcastResult(server, "Все игроки полностью восстановлены!");
                },
                "Сделать всех игроков очень голодными",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 4));
                        player.getFoodData().setFoodLevel(2);
                    }
                    broadcastResult(server, "Все игроки стали очень голодными!");
                }
        ));

        // Event 4: Day vs Night
        events.add(new VotingEvent(
                "Установить время суток — рассвет",
                server -> {
                    for (ServerLevel level : server.getAllLevels()) {
                        level.setDayTime(0);
                    }
                    broadcastResult(server, "Наступил рассвет!");
                },
                "Установить время суток — полночь",
                server -> {
                    for (ServerLevel level : server.getAllLevels()) {
                        level.setDayTime(18000);
                    }
                    broadcastResult(server, "Наступила полночь!");
                }
        ));

        // Event 5: Creepers vs Clear weather
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
                    broadcastResult(server, "Вокруг игроков появились крипперы!");
                },
                "Очистить погоду — ясный день",
                server -> {
                    for (ServerLevel level : server.getAllLevels()) {
                        level.setWeatherParameters(6000, 0, false, false);
                    }
                    broadcastResult(server, "Погода стала ясной!");
                }
        ));

        // Event 6: Give diamonds vs Take all items
        events.add(new VotingEvent(
                "Дать всем игрокам 5 алмазов",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ItemStack diamonds = new ItemStack(Items.DIAMOND, 5);
                        player.getInventory().add(diamonds);
                    }
                    broadcastResult(server, "Все игроки получили алмазы!");
                },
                "Дать всем игрокам 16 гнилого мяса",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ItemStack rotten = new ItemStack(Items.ROTTEN_FLESH, 16);
                        player.getInventory().add(rotten);
                    }
                    broadcastResult(server, "Все игроки получили гнилое мясо!");
                }
        ));

        // Event 7: Blindness vs Night vision
        events.add(new VotingEvent(
                "Дать всем игрокам ночное зрение на 2 минуты",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0));
                    }
                    broadcastResult(server, "Все игроки получили ночное зрение!");
                },
                "Дать всем игрокам слепоту на 30 секунд",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 600, 0));
                    }
                    broadcastResult(server, "Все игроки ослепли!");
                }
        ));

        // Event 8: Strength vs Weakness
        events.add(new VotingEvent(
                "Дать всем игрокам силу на 2 минуты",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2400, 1));
                    }
                    broadcastResult(server, "Все игроки получили силу!");
                },
                "Дать всем игрокам слабость на 2 минуты",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 2400, 1));
                    }
                    broadcastResult(server, "Все игроки получили слабость!");
                }
        ));

        // Event 9: Levitation vs Jump boost
        events.add(new VotingEvent(
                "Дать всем игрокам прыгучесть на 1 минуту",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.JUMP, 1200, 4));
                    }
                    broadcastResult(server, "Все игроки прыгают высоко!");
                },
                "Дать всем игрокам левитацию на 10 секунд",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 2));
                    }
                    broadcastResult(server, "Все игроки взлетели!");
                }
        ));

        // Event 10: Lightning strike vs Fire resistance
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
                    broadcastResult(server, "Молния ударила рядом с игроками!");
                },
                "Дать всем игрокам огнеупорность на 3 минуты",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 3600, 0));
                    }
                    broadcastResult(server, "Все игроки получили огнеупорность!");
                }
        ));

        // Event 11: Haste vs Mining fatigue
        events.add(new VotingEvent(
                "Дать всем игрокам бодрость на 2 минуты",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 2400, 2));
                    }
                    broadcastResult(server, "Все игроки копают быстрее!");
                },
                "Дать всем игрокам усталость копания на 2 минуты",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 2400, 2));
                    }
                    broadcastResult(server, "Все игроки копают медленнее!");
                }
        ));

        // Event 12: Poison vs Regeneration
        events.add(new VotingEvent(
                "Дать всем игрокам регенерацию на 30 секунд",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
                    }
                    broadcastResult(server, "Все игроки быстро восстанавливаются!");
                },
                "Отравить всех игроков на 15 секунд",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 1));
                    }
                    broadcastResult(server, "Все игроки отравлены!");
                }
        ));

        return events;
    }

    private static void broadcastResult(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Голосование] §e" + message), false);
    }
}
