package com.zahrproject.votingmod.item;

import com.zahrproject.votingmod.VotingMod;
import com.zahrproject.votingmod.sound.ModSounds;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VotingMod.MOD_ID);

    /** Custom music disc given out by the voting event. */
    public static final RegistryObject<Item> VOTING_DISC = ITEMS.register(
            "voting_disc",
            () -> new RecordItem(
                    1,                                    // comparator output signal strength (1–15)
                    ModSounds.VOTING_DISC_MUSIC,          // sound supplier
                    new Item.Properties().stacksTo(1),
                    6000                                  // track length in ticks (5 min placeholder)
            )
    );
}
