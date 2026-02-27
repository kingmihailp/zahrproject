package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.VotingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet.
 * Carries the player's vote choice (0 = option A, 1 = option B).
 */
public class VotePacket {

    private final int choice;

    public VotePacket(int choice) {
        this.choice = choice;
    }

    public static void encode(VotePacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.choice);
    }

    public static VotePacket decode(FriendlyByteBuf buf) {
        return new VotePacket(buf.readInt());
    }

    public static void handle(VotePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && (packet.choice == 0 || packet.choice == 1)) {
                VotingManager.getInstance().receiveVote(sender.getUUID(), packet.choice);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
