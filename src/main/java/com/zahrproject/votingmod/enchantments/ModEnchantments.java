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
}
