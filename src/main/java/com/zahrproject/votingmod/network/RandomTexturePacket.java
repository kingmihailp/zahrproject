package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.RandomTextureHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client to shuffle (or restore) all block/item atlas textures.
 */
public class RandomTexturePacket {

    private final boolean active;

    public RandomTexturePacket(boolean active) {
        this.active = active;
    }

    public static void encode(RandomTexturePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
    }

    public static RandomTexturePacket decode(FriendlyByteBuf buf) {
        return new RandomTexturePacket(buf.readBoolean());
    }

    public static void handle(RandomTexturePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> RandomTextureHandler.setActive(packet.active));
        ctx.get().setPacketHandled(true);
    }
}
