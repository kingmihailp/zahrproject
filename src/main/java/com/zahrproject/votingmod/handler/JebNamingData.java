package com.zahrproject.votingmod.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persists whether the "_jeb" naming ability has been unlocked globally.
 * Stored in the overworld's data storage so it survives server restarts.
 */
public class JebNamingData extends SavedData {

    private static final String DATA_NAME = "votingmod_jeb_naming";

    private boolean abilityEnabled = false;

    public JebNamingData() {}

    public static JebNamingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                JebNamingData::load,
                JebNamingData::new,
                DATA_NAME
        );
    }

    public boolean isEnabled() {
        return abilityEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.abilityEnabled = enabled;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("enabled", abilityEnabled);
        return tag;
    }

    public static JebNamingData load(CompoundTag tag) {
        JebNamingData data = new JebNamingData();
        data.abilityEnabled = tag.getBoolean("enabled");
        return data;
    }
}
