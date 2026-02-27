package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.VotingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client which option won and the vote counts.
 */
public class VoteResultPacket {

    private final int winnerOption; // 0 = A, 1 = B
    private final String winnerText;
    private final int votesA;
    private final int votesB;

    public VoteResultPacket(int winnerOption, String winnerText, int votesA, int votesB) {
        this.winnerOption = winnerOption;
        this.winnerText = winnerText;
        this.votesA = votesA;
        this.votesB = votesB;
    }

    public static void encode(VoteResultPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.winnerOption);
        buf.writeUtf(packet.winnerText, 512);
        buf.writeInt(packet.votesA);
        buf.writeInt(packet.votesB);
    }

    public static VoteResultPacket decode(FriendlyByteBuf buf) {
        int winner = buf.readInt();
        String text = buf.readUtf(512);
        int a = buf.readInt();
        int b = buf.readInt();
        return new VoteResultPacket(winner, text, a, b);
    }

    public static void handle(VoteResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof VotingScreen votingScreen) {
                votingScreen.showResult(packet.winnerOption, packet.winnerText, packet.votesA, packet.votesB);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
