package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Sends two randomly-picked event descriptions for the player to choose between.
 */
public class OpenVotingScreenPacket {

    private final String optionA;
    private final String optionB;
    private final long   durationSeconds;

    public OpenVotingScreenPacket(String optionA, String optionB, long durationSeconds) {
        this.optionA         = optionA;
        this.optionB         = optionB;
        this.durationSeconds = durationSeconds;
    }

    public static void encode(OpenVotingScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.optionA, 512);
        buf.writeUtf(packet.optionB, 512);
        buf.writeLong(packet.durationSeconds);
    }

    public static OpenVotingScreenPacket decode(FriendlyByteBuf buf) {
        String a   = buf.readUtf(512);
        String b   = buf.readUtf(512);
        long   dur = buf.readLong();
        return new OpenVotingScreenPacket(a, b, dur);
    }

    public static void handle(OpenVotingScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandlers.handleOpenVotingScreen(
                                packet.optionA, packet.optionB, packet.durationSeconds)));
        ctx.get().setPacketHandled(true);
    }
}
