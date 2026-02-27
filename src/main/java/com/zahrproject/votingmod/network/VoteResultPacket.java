package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.VotingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client whether YES or NO won and the vote counts.
 * The VotingScreen already knows the event description, so it is not repeated here.
 */
public class VoteResultPacket {

    private final boolean yesWon;
    private final int yesCount;
    private final int noCount;

    public VoteResultPacket(boolean yesWon, int yesCount, int noCount) {
        this.yesWon   = yesWon;
        this.yesCount = yesCount;
        this.noCount  = noCount;
    }

    public static void encode(VoteResultPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.yesWon);
        buf.writeInt(packet.yesCount);
        buf.writeInt(packet.noCount);
    }

    public static VoteResultPacket decode(FriendlyByteBuf buf) {
        boolean yesWon   = buf.readBoolean();
        int     yesCount = buf.readInt();
        int     noCount  = buf.readInt();
        return new VoteResultPacket(yesWon, yesCount, noCount);
    }

    public static void handle(VoteResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof VotingScreen votingScreen) {
                votingScreen.showResult(packet.yesWon, packet.yesCount, packet.noCount);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
