package com.zahrproject.votingmod.events;

import com.zahrproject.votingmod.VotingManager;
import com.zahrproject.votingmod.handler.FlipModelTracker;
import com.zahrproject.votingmod.handler.FlipScreenTracker;
import com.zahrproject.votingmod.handler.GoldenPlayerHandler;
import com.zahrproject.votingmod.handler.HardcoreModeHandler;
import com.zahrproject.votingmod.handler.HostileVillagersHandler;
import com.zahrproject.votingmod.handler.JailHandler;
import com.zahrproject.votingmod.handler.RaiderWaveHandler;
import com.zahrproject.votingmod.network.EventTimerPacket;
import com.zahrproject.votingmod.network.FlipModelPacket;
import com.zahrproject.votingmod.network.FlipScreenPacket;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * All individual voting events.
 * Players vote YES (event happens) or NO (event is cancelled).
 */
public class VotingEventList {

    private static final Random RANDOM = new Random();

    private static final ScheduledExecutorService FLIP_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-FlipRevert");
                t.setDaemon(true);
                return t;
            });

    private static final ScheduledExecutorService FLIP_MODEL_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VotingMod-FlipModelRevert");
                t.setDaemon(true);
                return t;
            });

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
                "Зомби-апокалипсис: нашествие зомби со всех сторон",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        for (int i = 0; i < 25; i++) {
                            Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
                            double ox = (RANDOM.nextDouble() - 0.5) * 30; // ±15 блоков
                            double oz = (RANDOM.nextDouble() - 0.5) * 30;
                            zombie.moveTo(pos.getX() + ox, pos.getY(), pos.getZ() + oz, 0, 0);
                            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                                    MobSpawnType.EVENT, null, null);
                            level.addFreshEntity(zombie);
                        }
                    }
                    broadcast(server, "ЗОМБИ-АПОКАЛИПСИС! Их слишком много!");
                }
        ));

        events.add(new VotingEvent(
                "Призвать бронированного зомби с рандомным снаряжением",
                server -> {
                    // Пулы предметов для случайного снаряжения
                    Item[] helmets   = { Items.LEATHER_HELMET,    Items.IRON_HELMET,
                                         Items.GOLDEN_HELMET,    Items.DIAMOND_HELMET,
                                         Items.NETHERITE_HELMET                         };
                    Item[] chests    = { Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE,
                                         Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE,
                                         Items.NETHERITE_CHESTPLATE                      };
                    Item[] legs      = { Items.LEATHER_LEGGINGS,  Items.IRON_LEGGINGS,
                                         Items.GOLDEN_LEGGINGS,  Items.DIAMOND_LEGGINGS,
                                         Items.NETHERITE_LEGGINGS                        };
                    Item[] boots     = { Items.LEATHER_BOOTS,     Items.IRON_BOOTS,
                                         Items.GOLDEN_BOOTS,     Items.DIAMOND_BOOTS,
                                         Items.NETHERITE_BOOTS                           };
                    Item[] weapons   = { Items.WOODEN_SWORD, Items.STONE_SWORD,
                                         Items.IRON_SWORD,   Items.GOLDEN_SWORD,
                                         Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
                                         Items.IRON_AXE,     Items.DIAMOND_AXE,
                                         Items.NETHERITE_AXE                             };

                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        double ox = (RANDOM.nextDouble() - 0.5) * 6;
                        double oz = (RANDOM.nextDouble() - 0.5) * 6;

                        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
                        zombie.moveTo(pos.getX() + ox, pos.getY(), pos.getZ() + oz, 0, 0);
                        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                                MobSpawnType.EVENT, null, null);

                        zombie.setItemSlot(EquipmentSlot.HEAD,     new ItemStack(helmets [RANDOM.nextInt(helmets.length)]));
                        zombie.setItemSlot(EquipmentSlot.CHEST,    new ItemStack(chests  [RANDOM.nextInt(chests.length)]));
                        zombie.setItemSlot(EquipmentSlot.LEGS,     new ItemStack(legs    [RANDOM.nextInt(legs.length)]));
                        zombie.setItemSlot(EquipmentSlot.FEET,     new ItemStack(boots   [RANDOM.nextInt(boots.length)]));
                        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons [RANDOM.nextInt(weapons.length)]));

                        // Небольшой шанс дропа снаряжения
                        for (EquipmentSlot slot : EquipmentSlot.values()) {
                            zombie.setDropChance(slot, 0.05f);
                        }

                        level.addFreshEntity(zombie);
                    }
                    broadcast(server, "Появился бронированный зомби! Берегись!");
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

        events.add(new VotingEvent(
                "Убить всех игроков",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.kill();
                    }
                    broadcast(server, "Все игроки мертвы. Ничего личного.");
                }
        ));

        events.add(new VotingEvent(
                "Выдать всем игрокам случайный эффект на 20 секунд",
                server -> {
                    List<MobEffect> effects = new ArrayList<>(BuiltInRegistries.MOB_EFFECT.stream().toList());
                    if (effects.isEmpty()) return;
                    MobEffect chosen = effects.get(RANDOM.nextInt(effects.size()));
                    String effectName = chosen.getDisplayName().getString();
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.addEffect(new MobEffectInstance(chosen, 400, 0)); // 400 тиков = 20 секунд
                    }
                    broadcast(server, "Все игроки получили эффект: " + effectName + "!");
                }
        ));

        // ── Items ──────────────────────────────────────────────────────────────

        // ── Special: TNT rain ──────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Дождь из динамита над каждым игроком",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        for (int i = 0; i < 7; i++) {
                            double ox = (RANDOM.nextDouble() - 0.5) * 10; // ±5 блоков
                            double oz = (RANDOM.nextDouble() - 0.5) * 10;
                            double oy = 3 + RANDOM.nextInt(2);             // +3 или +4 блока
                            PrimedTnt tnt = new PrimedTnt(level,
                                    pos.getX() + ox,
                                    pos.getY() + oy,
                                    pos.getZ() + oz,
                                    null);
                            tnt.setFuse(40 + RANDOM.nextInt(21)); // 2–3 секунды
                            level.addFreshEntity(tnt);
                        }
                    }
                    broadcast(server, "Дождь из динамита! Спасайся кто может!");
                }
        ));

        // ── Special: random mob from registry ──────────────────────────────────

        events.add(new VotingEvent(
                "Призвать случайного моба рядом с каждым игроком",
                server -> {
                    // Collect all entity types that are actual mobs (non-MISC categories)
                    List<EntityType<?>> spawnable = new ArrayList<>();
                    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                        if (type.getCategory() != MobCategory.MISC) {
                            spawnable.add(type);
                        }
                    }
                    if (spawnable.isEmpty()) return;

                    EntityType<?> chosen = spawnable.get(RANDOM.nextInt(spawnable.size()));
                    String mobName = chosen.getDescription().getString();

                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        double ox = (RANDOM.nextDouble() - 0.5) * 6;
                        double oz = (RANDOM.nextDouble() - 0.5) * 6;
                        Entity entity = chosen.create(level);
                        if (entity == null) continue;
                        entity.moveTo(pos.getX() + ox, pos.getY(), pos.getZ() + oz, 0, 0);
                        if (entity instanceof Mob mob) {
                            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                                    MobSpawnType.EVENT, null, null);
                        }
                        level.addFreshEntity(entity);
                    }
                    broadcast(server, "Случайный моб появился рядом с игроками: " + mobName + "!");
                }
        ));

        // ── Special: children ──────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Обратно в детство",
                server -> {
                    ChildEventManager.activate(server, 8 * 60);
                    broadcast(server, "Обратно в детство! Все игроки стали детьми на 8 минут!");
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

        // ── Special: prison ───────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Заключить каждого игрока в мини-тюрьму",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();

                        // Floor (Y-1) и Ceiling (Y+2) — каменные кирпичи
                        for (int x = -2; x <= 2; x++)
                            for (int z = -2; z <= 2; z++) {
                                level.setBlock(pos.offset(x, -1, z),
                                        Blocks.STONE_BRICKS.defaultBlockState(), 3);
                                level.setBlock(pos.offset(x,  2, z),
                                        Blocks.STONE_BRICKS.defaultBlockState(), 3);
                            }

                        // Стены (Y=0,1): решётки по периметру, воздух внутри
                        for (int y = 0; y <= 1; y++)
                            for (int x = -2; x <= 2; x++)
                                for (int z = -2; z <= 2; z++) {
                                    boolean isWall = (x == -2 || x == 2 || z == -2 || z == 2);
                                    level.setBlock(pos.offset(x, y, z),
                                            isWall ? Blocks.IRON_BARS.defaultBlockState()
                                                   : Blocks.AIR.defaultBlockState(), 3);
                                }
                    }
                    broadcast(server, "Все игроки заперты в тюрьме!");
                }
        ));

        // ── Special: skateboard book ───────────────────────────────────────────

        events.add(new VotingEvent(
                "Выдать каждому игроку зачарованную книгу «Скейтборд»",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                        EnchantedBookItem.addEnchantment(book,
                                new EnchantmentInstance(ModEnchantments.SKATEBOARD.get(), 1));
                        player.getInventory().add(book);
                    }
                    broadcast(server, "Все получили зачарованную книгу «Скейтборд»! "
                            + "Зачаруйте щит, возьмите его в левую руку и жмите Ctrl!");
                }
        ));

        // ── Special: boat ─────────────────────────────────────────────────────

        events.add(new VotingEvent(
                "Призвать случайную лодку рядом с каждым игроком",
                server -> {
                    // Собираем все типы лодок и плотов из реестра по суффиксу имени
                    List<EntityType<?>> boatTypes = new ArrayList<>();
                    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                        if (key == null) continue;
                        String path = key.getPath();
                        if (path.endsWith("_boat") || path.endsWith("_raft")) {
                            boatTypes.add(type);
                        }
                    }
                    if (boatTypes.isEmpty()) return;

                    EntityType<?> boatType = boatTypes.get(RANDOM.nextInt(boatTypes.size()));
                    String boatName = boatType.getDescription().getString();

                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        double ox = (RANDOM.nextDouble() - 0.5) * 4;
                        double oz = (RANDOM.nextDouble() - 0.5) * 4;
                        Entity boat = boatType.create(level);
                        if (boat == null) continue;
                        boat.moveTo(pos.getX() + ox, pos.getY(), pos.getZ() + oz, 0, 0);
                        level.addFreshEntity(boat);
                    }
                    broadcast(server, "Рядом с игроками появилась " + boatName + "!");
                }
        ));

        // ── Special: random structure ──────────────────────────────────────────

        events.add(new VotingEvent(
                "Заспавнить случайную структуру рядом с игроком",
                server -> {
                    Registry<Structure> structureRegistry =
                            server.registryAccess().registryOrThrow(Registries.STRUCTURE);
                    List<ResourceKey<Structure>> keys = new ArrayList<>(structureRegistry.registryKeySet());
                    if (keys.isEmpty()) return;

                    ResourceKey<Structure> chosen = keys.get(RANDOM.nextInt(keys.size()));
                    String structureId = chosen.location().toString();

                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        int ox = RANDOM.nextInt(81) - 40; // ±40 блоков
                        int oz = RANDOM.nextInt(81) - 40;
                        int tx = pos.getX() + ox;
                        int tz = pos.getZ() + oz;
                        int ty = level.getHeight(Heightmap.Types.WORLD_SURFACE, tx, tz);

                        server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack(),
                                "place structure " + structureId + " " + tx + " " + ty + " " + tz
                        );
                    }
                    broadcast(server, "Структура \"" + chosen.location().getPath() + "\" появилась неподалёку!");
                }
        ));

        // ── Special: random teleport ──────────────────────────────────────────

        events.add(new VotingEvent(
                "Телепортировать всех игроков в случайные точки мира",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        int tx = RANDOM.nextInt(10001) - 5000; // [-5000 … 5000]
                        int tz = RANDOM.nextInt(10001) - 5000;
                        // Force-load the destination chunk so the WORLD_SURFACE
                        // heightmap is populated; without this getHeight() returns
                        // getMinBuildHeight()-1 for unloaded chunks (≈ -65).
                        level.getChunk(tx >> 4, tz >> 4);
                        int ty = level.getHeight(Heightmap.Types.WORLD_SURFACE, tx, tz);
                        player.teleportTo(tx + 0.5, ty, tz + 0.5);
                    }
                    broadcast(server, "Все игроки телепортированы в случайные места мира!");
                }
        ));

        // ── Special: killer rabbit ────────────────────────────────────────────

        events.add(new VotingEvent(
                "Призвать кролика-убийцу рядом с каждым игроком",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        double ox = (RANDOM.nextDouble() - 0.5) * 6;
                        double oz = (RANDOM.nextDouble() - 0.5) * 6;
                        Rabbit rabbit = new Rabbit(EntityType.RABBIT, level);
                        rabbit.moveTo(pos.getX() + ox, pos.getY(), pos.getZ() + oz, 0, 0);
                        rabbit.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                                MobSpawnType.EVENT, null, null);
                        rabbit.setVariant(Rabbit.Variant.EVIL);
                        level.addFreshEntity(rabbit);
                    }
                    broadcast(server, "Кролик-убийца появился рядом с игроками! СПАСАЙТЕСЬ!");
                }
        ));

        // ── Special: random experience ────────────────────────────────────────

        events.add(new VotingEvent(
                "Выдать всем игрокам случайное количество опыта",
                server -> {
                    int amount = 10 + RANDOM.nextInt(991); // [10 … 1000]
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.giveExperiencePoints(amount);
                    }
                    broadcast(server, "Все игроки получили " + amount + " очков опыта!");
                }
        ));

        // ── Special: random enchantment on a random inventory item ────────────

        events.add(new VotingEvent(
                "Зачаровать случайный предмет каждого игрока случайным чаром",
                server -> {
                    // Collect all registered enchantments from the Forge registry
                    List<Enchantment> allEnchants =
                            new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());
                    if (allEnchants.isEmpty()) return;

                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        // Gather every non-empty item from main inventory + armor + offhand
                        List<ItemStack> candidates = new ArrayList<>();
                        player.getInventory().items.forEach(s -> { if (!s.isEmpty()) candidates.add(s); });
                        player.getInventory().armor.forEach(s -> { if (!s.isEmpty()) candidates.add(s); });
                        player.getInventory().offhand.forEach(s -> { if (!s.isEmpty()) candidates.add(s); });

                        if (candidates.isEmpty()) continue;

                        ItemStack target = candidates.get(RANDOM.nextInt(candidates.size()));
                        Enchantment enchant = allEnchants.get(RANDOM.nextInt(allEnchants.size()));
                        // Pick a random level between 1 and the enchantment's max level
                        int level = 1 + RANDOM.nextInt(enchant.getMaxLevel());
                        target.enchant(enchant, level);

                        String enchantName = enchant.getFullname(level).getString();
                        player.sendSystemMessage(Component.literal(
                                "§6[Голосование] §eТвой предмет получил чар: " + enchantName + "!"));
                    }
                    broadcast(server, "Каждый игрок получил случайный чар на один из своих предметов!");
                }
        ));

        // ── Special: pyrotechnics ──────────────────────────────────────────────

        events.add(new VotingEvent(
                "Пиротехника",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();

                        // Строим фейерверк: полёт 8, большой шар, мерцание, след
                        ItemStack fireworkItem = new ItemStack(Items.FIREWORK_ROCKET);
                        CompoundTag tag = fireworkItem.getOrCreateTag();
                        CompoundTag fireworksTag = new CompoundTag();
                        fireworksTag.putByte("Flight", (byte) 8);
                        ListTag explosionsList = new ListTag();
                        CompoundTag explosionTag = new CompoundTag();
                        explosionTag.putByte("Type", (byte) 1); // Large Ball
                        explosionTag.putBoolean("Flicker", true);
                        explosionTag.putBoolean("Trail", true);
                        explosionTag.putIntArray("Colors",
                                new int[]{0xFF0000, 0xFFFF00, 0x00FFFF});
                        explosionsList.add(explosionTag);
                        fireworksTag.put("Explosions", explosionsList);
                        tag.put("Fireworks", fireworksTag);

                        // Спавним фейерверк прямо под игроком, даём ему скорость вверх
                        // и сажаем игрока верхом
                        FireworkRocketEntity firework = new FireworkRocketEntity(level,
                                player.getX(), player.getY(), player.getZ(),
                                fireworkItem);
                        firework.setDeltaMovement(0.0, 1.8, 0.0);
                        level.addFreshEntity(firework);
                        player.startRiding(firework, true);
                    }
                    broadcast(server, "ПИРОТЕХНИКА! Все игроки улетели в небо!");
                }
        ));

        // ── Special: anvil drop ───────────────────────────────────────────────

        events.add(new VotingEvent(
                "Тяжёлая дума",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition();
                        FallingBlockEntity anvil = FallingBlockEntity.fall(
                                level, pos.above(15), Blocks.ANVIL.defaultBlockState());
                        anvil.dropItem = false;
                    }
                    broadcast(server, "Тяжёлая дума! Наковальни падают на игроков!");
                }
        ));

        // ── Special: cobweb cube ──────────────────────────────────────────────

        events.add(new VotingEvent(
                "Отчаянное положение",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        BlockPos center = player.blockPosition();
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = 0; dy <= 2; dy++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    level.setBlock(center.offset(dx, dy, dz),
                                            Blocks.COBWEB.defaultBlockState(), 3);
                                }
                            }
                        }
                    }
                    broadcast(server, "Отчаянное положение! Все игроки замурованы в паутине!");
                }
        ));

        // ── Special: charged creeper ──────────────────────────────────────────

        events.add(new VotingEvent(
                "Взрывной характер",
                server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = player.serverLevel();
                        Creeper creeper = new Creeper(EntityType.CREEPER, level);
                        CompoundTag creeperTag = new CompoundTag();
                        creeperTag.putBoolean("powered", true);
                        creeper.readAdditionalSaveData(creeperTag);
                        creeper.moveTo(player.getX() + 2.5, player.getY(), player.getZ(), 0, 0);
                        level.addFreshEntity(creeper);
                    }
                    broadcast(server, "Взрывной характер! Заряженные криперы спавнятся рядом с игроками!");
                }
        ));

        // ── Special: random hoe ───────────────────────────────────────────────

        events.add(new VotingEvent(
                "Заявка на мотыгу",
                server -> {
                    List<Item> hoes = List.of(
                            Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE,
                            Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
                    Random rng = new Random();
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        Item hoe = hoes.get(rng.nextInt(hoes.size()));
                        player.addItem(new ItemStack(hoe));
                    }
                    broadcast(server, "Заявка на мотыгу! Каждый игрок получил случайную мотыгу!");
                }
        ));

        // ── Special: shorten vote interval ────────────────────────────────────

        events.add(new VotingEvent(
                "Больше голосований",
                server -> {
                    VotingManager vm = VotingManager.getInstance();
                    long newInterval = Math.max(20, vm.getIntervalSeconds() - 20);
                    vm.setInterval(newInterval);
                    broadcast(server, "Больше голосований! Интервал сокращён до " + newInterval + " сек.");
                }
        ));

        // ── Special: keep inventory toggle ────────────────────────────────────

        events.add(new VotingEvent(
                "Переключить keepInventory",
                server -> {
                    boolean current = server.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
                    server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(!current, server);
                    broadcast(server, "keepInventory " + (!current
                            ? "включён! Предметы сохраняются при смерти."
                            : "выключен! Предметы снова теряются при смерти."));
                }
        ));

        // ── Special: passive aggression ────────────────────────────────────────

        events.add(new VotingEvent(
                "Пассивная агрессия",
                server -> {
                    long duration = 10L * 60 * 1000;
                    HostileVillagersHandler.activate(server, duration);
                    broadcast(server, "Пассивная агрессия! Жители разозлились и нападают на игроков 10 минут!");
                }
        ));

        // ── Special: hardcore mode ────────────────────────────────────────────

        events.add(new VotingEvent(
                "Выше, сильнее, сложнее",
                server -> {
                    long duration = 5L * 60 * 1000;
                    HardcoreModeHandler.activate(server, duration);
                    broadcast(server, "Выше, сильнее, сложнее! Хардкор на 5 минут: сложность Hard, умрёшь — будешь наблюдателем до конца!");
                }
        ));

        // ── Special: jail with silverfish ────────────────────────────────────

        events.add(new VotingEvent(
                "Под шхонкой",
                server -> {
                    long duration = 2L * 60 * 1000;
                    JailHandler.activate(server, duration);
                    broadcast(server, "Под шхонкой! Все игроки заперты в клетке с чешуйницами на 2 минуты!");
                }
        ));

        // ── Special: raider wave ──────────────────────────────────────────────

        events.add(new VotingEvent(
                "Они следят, они ищут",
                server -> {
                    int wave = RaiderWaveHandler.activate(server);
                    broadcast(server, "Они следят, они ищут! Волна рейдеров #" + wave + " появилась рядом с каждым игроком!");
                }
        ));

        // ── Special: flip player models ───────────────────────────────────────

        events.add(new VotingEvent(
                "Голова вниз",
                server -> {
                    long durationMs = 10L * 60 * 1000;
                    FlipModelTracker.setActive(System.currentTimeMillis() + durationMs, durationMs);
                    FlipModelPacket pktOn     = new FlipModelPacket(true);
                    EventTimerPacket timerStart = new EventTimerPacket(
                            FlipModelTracker.TIMER_NAME, durationMs, durationMs);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pktOn);
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);
                    }
                    broadcast(server, "Голова вниз! Все игроки перевёрнуты на 10 минут!");
                    FLIP_MODEL_SCHEDULER.schedule(() -> {
                        FlipModelTracker.clear();
                        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
                        if (srv == null) return;
                        srv.execute(() -> {
                            FlipModelPacket pktOff  = new FlipModelPacket(false);
                            EventTimerPacket timerEnd = new EventTimerPacket(
                                    FlipModelTracker.TIMER_NAME, 0, 0);
                            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pktOff);
                                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerEnd);
                            }
                            broadcast(srv, "Всё в порядке! Игроки снова стоят правильно.");
                        });
                    }, durationMs, TimeUnit.MILLISECONDS);
                }
        ));

        // ── Special: golden player ────────────────────────────────────────────

        events.add(new VotingEvent(
                "Прикосновение Мидаса",
                server -> {
                    long durationMs = 3 * 60 * 1000L;
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        GoldenPlayerHandler.makeGolden(player, durationMs);
                    }
                    GoldenPlayerHandler.syncToAll(server, durationMs);
                    broadcast(server, "Прикосновение Мидаса! Все игроки превратились в золотых на 2 минуты!");
                }
        ));

        // ── Special: flip screen ──────────────────────────────────────────────

        events.add(new VotingEvent(
                "Все вверх дном",
                server -> {
                    long durationMs = 3 * 60 * 1000L;
                    FlipScreenTracker.setActive(System.currentTimeMillis() + durationMs, durationMs);
                    FlipScreenPacket pktOn    = new FlipScreenPacket(true);
                    EventTimerPacket timerStart = new EventTimerPacket("Все вверх дном", durationMs, durationMs);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pktOn);
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerStart);
                    }
                    broadcast(server, "Все вверх дном! Экраны всех игроков перевёрнуты на 3 минуты!");
                    FLIP_SCHEDULER.schedule(() -> {
                        FlipScreenTracker.clear();
                        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
                        if (srv == null) return;
                        srv.execute(() -> {
                            FlipScreenPacket pktOff  = new FlipScreenPacket(false);
                            EventTimerPacket timerEnd = new EventTimerPacket("Все вверх дном", 0, 0);
                            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pktOff);
                                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), timerEnd);
                            }
                            broadcast(srv, "Всё на своих местах! Экраны игроков восстановлены.");
                        });
                    }, durationMs, TimeUnit.MILLISECONDS);
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
