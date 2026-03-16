package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.ClientPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client to start rendering a blinking TNT-style overlay
 * on the given block for {@code fuseMs} milliseconds, after which the
 * block will be blown up server-side.
 */
public class PrimedBlockPacket {

    private final BlockPos pos;
    private final long     fuseMs;

    public PrimedBlockPacket(BlockPos pos, long fuseMs) {
        this.pos    = pos;
        this.fuseMs = fuseMs;
    }

    public static void encode(PrimedBlockPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeLong(packet.fuseMs);
    }

    public static PrimedBlockPacket decode(FriendlyByteBuf buf) {
        return new PrimedBlockPacket(buf.readBlockPos(), buf.readLong());
    }

    public static void handle(PrimedBlockPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandlers.handlePrimedBlock(packet.pos, packet.fuseMs)));
        ctx.get().setPacketHandled(true);
    }
}
