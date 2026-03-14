package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client to enable or disable the "big head" rendering effect.
 */
public class BigHeadPacket {

    private final boolean active;

    public BigHeadPacket(boolean active) {
        this.active = active;
    }

    public static void encode(BigHeadPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
    }

    public static BigHeadPacket decode(FriendlyByteBuf buf) {
        return new BigHeadPacket(buf.readBoolean());
    }

    public static void handle(BigHeadPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandlers.handleBigHead(packet.active)));
        ctx.get().setPacketHandled(true);
    }
}
