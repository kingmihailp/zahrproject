package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client to apply (or remove) the colour-inversion screen filter.
 */
public class InvertColorsPacket {

    private final boolean active;

    public InvertColorsPacket(boolean active) {
        this.active = active;
    }

    public static void encode(InvertColorsPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
    }

    public static InvertColorsPacket decode(FriendlyByteBuf buf) {
        return new InvertColorsPacket(buf.readBoolean());
    }

    public static void handle(InvertColorsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandlers.handleInvertColors(packet.active)));
        ctx.get().setPacketHandled(true);
    }
}
