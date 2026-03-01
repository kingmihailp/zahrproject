package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.handler.GoldenPlayerHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Sends the full set of currently "golden" player UUIDs so the client
 * can render the gold overlay for every affected player, not just the local one.
 */
public class SyncGoldenStatePacket {

    private final Set<UUID> goldenUUIDs;

    public SyncGoldenStatePacket(Set<UUID> goldenUUIDs) {
        this.goldenUUIDs = goldenUUIDs;
    }

    public static void encode(SyncGoldenStatePacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.goldenUUIDs.size());
        for (UUID uuid : packet.goldenUUIDs) {
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
        }
    }

    public static SyncGoldenStatePacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        Set<UUID> uuids = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            uuids.add(new UUID(msb, lsb));
        }
        return new SyncGoldenStatePacket(uuids);
    }

    public static void handle(SyncGoldenStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> GoldenPlayerHandler.setGoldenPlayers(packet.goldenUUIDs));
        ctx.get().setPacketHandled(true);
    }
}
