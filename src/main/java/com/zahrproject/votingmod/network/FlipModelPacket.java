package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells every client to render all player models upside-down (or restore them).
 */
public class FlipModelPacket {

    private final boolean active;

    public FlipModelPacket(boolean active) {
        this.active = active;
    }

    public static void encode(FlipModelPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.active);
    }

    public static FlipModelPacket decode(FriendlyByteBuf buf) {
        return new FlipModelPacket(buf.readBoolean());
    }

    public static void handle(FlipModelPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandlers.handleFlipModel(pkt.active)));
        ctx.get().setPacketHandled(true);
    }
}
