package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.events.ChildEventManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Sends the full set of "child" player UUIDs so the client can apply
 * the correct hitbox and rendering for each player.
 */
public class SyncChildStatePacket {

    private final Set<UUID> childUUIDs;

    public SyncChildStatePacket(Set<UUID> childUUIDs) {
        this.childUUIDs = childUUIDs;
    }

    public static void encode(SyncChildStatePacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.childUUIDs.size());
        for (UUID uuid : packet.childUUIDs) {
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
        }
    }

    public static SyncChildStatePacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        Set<UUID> uuids = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            uuids.add(new UUID(msb, lsb));
        }
        return new SyncChildStatePacket(uuids);
    }

    public static void handle(SyncChildStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ChildEventManager.setChildPlayers(packet.childUUIDs);
            // Refresh bounding boxes so the client uses the correct hitbox immediately
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    for (Player player : mc.level.players()) {
                        player.refreshDimensions();
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
