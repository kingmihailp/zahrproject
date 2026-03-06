package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.InvertColorsHandler;
import net.minecraft.network.FriendlyByteBuf;
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
        ctx.get().enqueueWork(() -> InvertColorsHandler.setActive(packet.active));
        ctx.get().setPacketHandled(true);
    }
}
