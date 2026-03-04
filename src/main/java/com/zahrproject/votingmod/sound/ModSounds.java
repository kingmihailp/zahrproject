package com.zahrproject.votingmod.sound;

import com.zahrproject.votingmod.VotingMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, VotingMod.MOD_ID);

    /** Music track for the custom voting disc. Add the .ogg file later. */
    public static final RegistryObject<SoundEvent> VOTING_DISC_MUSIC = SOUNDS.register(
            "music.disc.voting",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(VotingMod.MOD_ID, "music.disc.voting"))
    );
}
