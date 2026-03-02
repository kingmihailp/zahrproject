package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.ScreenFlipHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client to flip (or restore) their camera roll by 180 degrees.
 */
public class FlipScreenPacket {

    private final boolean flipped;

    public FlipScreenPacket(boolean flipped) {
        this.flipped = flipped;
    }

    public static void encode(FlipScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.flipped);
    }

    public static FlipScreenPacket decode(FriendlyByteBuf buf) {
        return new FlipScreenPacket(buf.readBoolean());
    }

    public static void handle(FlipScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ScreenFlipHandler.setFlipped(packet.flipped));
        ctx.get().setPacketHandled(true);
    }
}
