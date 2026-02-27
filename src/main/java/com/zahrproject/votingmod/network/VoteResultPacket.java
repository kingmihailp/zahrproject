package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.VotingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client which option won (0=A, 1=B) and the vote counts.
 */
public class VoteResultPacket {

    private final int winnerOption; // 0 = A, 1 = B
    private final int votesA;
    private final int votesB;

    public VoteResultPacket(int winnerOption, int votesA, int votesB) {
        this.winnerOption = winnerOption;
        this.votesA       = votesA;
        this.votesB       = votesB;
    }

    public static void encode(VoteResultPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.winnerOption);
        buf.writeInt(packet.votesA);
        buf.writeInt(packet.votesB);
    }

    public static VoteResultPacket decode(FriendlyByteBuf buf) {
        int winner = buf.readInt();
        int a      = buf.readInt();
        int b      = buf.readInt();
        return new VoteResultPacket(winner, a, b);
    }

    public static void handle(VoteResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof VotingScreen screen) {
                screen.showResult(packet.winnerOption, packet.votesA, packet.votesB);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
