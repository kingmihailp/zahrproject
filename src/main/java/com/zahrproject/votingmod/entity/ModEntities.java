package com.zahrproject.votingmod.entity;

import com.zahrproject.votingmod.VotingMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, VotingMod.MOD_ID);

    public static final RegistryObject<EntityType<BlackHoleEntity>> BLACK_HOLE =
            ENTITIES.register("black_hole", () ->
                    EntityType.Builder.<BlackHoleEntity>of(BlackHoleEntity::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(256)
                            .updateInterval(1)
                            .build("black_hole"));
}
