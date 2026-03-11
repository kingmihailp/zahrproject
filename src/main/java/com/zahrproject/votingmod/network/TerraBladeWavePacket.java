package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.handler.TerraBladeHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet.
 * Sent when the client detects a left-click-in-empty-air while holding
 * a Terra Blade-enchanted weapon. The server validates the enchantment
 * and cooldown, then spawns the wave.
 */
public class TerraBladeWavePacket {

    public static void encode(TerraBladeWavePacket packet, FriendlyByteBuf buf) {}

    public static TerraBladeWavePacket decode(FriendlyByteBuf buf) {
        return new TerraBladeWavePacket();
    }

    public static void handle(TerraBladeWavePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                TerraBladeHandler.trySpawnWave(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
