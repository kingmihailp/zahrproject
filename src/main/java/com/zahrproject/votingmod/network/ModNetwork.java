package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.VotingMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Registers all network packets for the mod.
 */
public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VotingMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        // S2C: Server sends to all clients to open the voting screen
        CHANNEL.registerMessage(id++,
                OpenVotingScreenPacket.class,
                OpenVotingScreenPacket::encode,
                OpenVotingScreenPacket::decode,
                OpenVotingScreenPacket::handle);

        // C2S: Client sends the player's vote choice to server
        CHANNEL.registerMessage(id++,
                VotePacket.class,
                VotePacket::encode,
                VotePacket::decode,
                VotePacket::handle);

        // S2C: Server sends vote result to all clients
        CHANNEL.registerMessage(id++,
                VoteResultPacket.class,
                VoteResultPacket::encode,
                VoteResultPacket::decode,
                VoteResultPacket::handle);

        // S2C: Server syncs the set of "child" player UUIDs to all clients
        CHANNEL.registerMessage(id++,
                SyncChildStatePacket.class,
                SyncChildStatePacket::encode,
                SyncChildStatePacket::decode,
                SyncChildStatePacket::handle);
    }
}
