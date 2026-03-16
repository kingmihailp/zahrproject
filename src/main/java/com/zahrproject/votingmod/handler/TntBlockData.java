package com.zahrproject.votingmod.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persists the "Это точно торттил?" event timer so it survives server restarts.
 * Stored in the overworld's data storage.
 */
public class TntBlockData extends SavedData {

    private static final String DATA_NAME = "votingmod_tntblock";

    private long expiryMs   = 0;
    private long durationMs = 0;

    public TntBlockData() {}

    public static TntBlockData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                TntBlockData::load,
                TntBlockData::new,
                DATA_NAME
        );
    }

    public long getExpiryMs()   { return expiryMs;   }
    public long getDurationMs() { return durationMs; }

    public void setExpiry(long expiry, long duration) {
        this.expiryMs   = expiry;
        this.durationMs = duration;
        setDirty();
    }

    public void clear() {
        this.expiryMs   = 0;
        this.durationMs = 0;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("expiry",   expiryMs);
        tag.putLong("duration", durationMs);
        return tag;
    }

    public static TntBlockData load(CompoundTag tag) {
        TntBlockData d = new TntBlockData();
        d.expiryMs   = tag.getLong("expiry");
        d.durationMs = tag.getLong("duration");
        return d;
    }
}
