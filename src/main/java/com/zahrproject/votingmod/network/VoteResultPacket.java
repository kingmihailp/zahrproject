package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells all clients the voting result so the result overlay can display it.
 */
public class VoteResultPacket {

    private final int    winnerOption; // 0 = A, 1 = B
    private final int    votesA;
    private final int    votesB;
    private final String optionA;
    private final String optionB;

    public VoteResultPacket(int winnerOption, int votesA, int votesB,
                            String optionA, String optionB) {
        this.winnerOption = winnerOption;
        this.votesA       = votesA;
        this.votesB       = votesB;
        this.optionA      = optionA;
        this.optionB      = optionB;
    }

    public static void encode(VoteResultPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.winnerOption);
        buf.writeInt(packet.votesA);
        buf.writeInt(packet.votesB);
        buf.writeUtf(packet.optionA);
        buf.writeUtf(packet.optionB);
    }

    public static VoteResultPacket decode(FriendlyByteBuf buf) {
        int    winner  = buf.readInt();
        int    a       = buf.readInt();
        int    b       = buf.readInt();
        String optionA = buf.readUtf();
        String optionB = buf.readUtf();
        return new VoteResultPacket(winner, a, b, optionA, optionB);
    }

    public static void handle(VoteResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandlers.handleVoteResult(
                                packet.winnerOption, packet.votesA, packet.votesB,
                                packet.optionA, packet.optionB)));
        ctx.get().setPacketHandled(true);
    }
}
