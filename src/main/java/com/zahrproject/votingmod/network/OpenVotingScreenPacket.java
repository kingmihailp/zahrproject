package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.VotingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Tells the client to open the voting screen for a single event.
 * Players will vote YES or NO on whether this event should happen.
 */
public class OpenVotingScreenPacket {

    private final String eventDescription;
    private final long durationSeconds;

    public OpenVotingScreenPacket(String eventDescription, long durationSeconds) {
        this.eventDescription = eventDescription;
        this.durationSeconds  = durationSeconds;
    }

    public static void encode(OpenVotingScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.eventDescription, 512);
        buf.writeLong(packet.durationSeconds);
    }

    public static OpenVotingScreenPacket decode(FriendlyByteBuf buf) {
        String desc = buf.readUtf(512);
        long dur    = buf.readLong();
        return new OpenVotingScreenPacket(desc, dur);
    }

    public static void handle(OpenVotingScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new VotingScreen(packet.eventDescription, packet.durationSeconds));
        });
        ctx.get().setPacketHandled(true);
    }
}
