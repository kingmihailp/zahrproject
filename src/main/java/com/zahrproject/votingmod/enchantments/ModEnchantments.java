package com.zahrproject.votingmod.enchantments;

import com.zahrproject.votingmod.VotingMod;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, VotingMod.MOD_ID);

    public static final RegistryObject<Enchantment> SKATEBOARD =
            ENCHANTMENTS.register("skateboard", SkateboardEnchantment::new);

    public static final RegistryObject<Enchantment> XSHOT =
            ENCHANTMENTS.register("xshot", XShotEnchantment::new);

    public static final RegistryObject<Enchantment> SUPER_PICKAXE =
            ENCHANTMENTS.register("super_pickaxe", SuperPickaxeEnchantment::new);

    public static final RegistryObject<Enchantment> MSHOT =
            ENCHANTMENTS.register("mshot", MShotEnchantment::new);

    public static final RegistryObject<Enchantment> ANTI_GRAVITY =
            ENCHANTMENTS.register("anti_gravity", AntiGravityEnchantment::new);

    public static final RegistryObject<Enchantment> WATER_BLADE =
            ENCHANTMENTS.register("water_blade", WaterBladeEnchantment::new);

    public static final RegistryObject<Enchantment> FIRE_BLADE =
            ENCHANTMENTS.register("fire_blade", FireBladeEnchantment::new);

    public static final RegistryObject<Enchantment> EARTH_BLADE =
            ENCHANTMENTS.register("earth_blade", EarthBladeEnchantment::new);

    public static final RegistryObject<Enchantment> WIND_BLADE =
            ENCHANTMENTS.register("wind_blade", WindBladeEnchantment::new);

    public static final RegistryObject<Enchantment> TERRA_BLADE =
            ENCHANTMENTS.register("terra_blade", TerraBladeEnchantment::new);
}
