package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.EventTimerHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Starts or removes a named event timer on the HUD.
 *   remainingMs > 0  → show/update timer for this event
 *   remainingMs <= 0 → remove timer for this event
 */
public class EventTimerPacket {

    private final String eventName;
    private final long remainingMs;

    public EventTimerPacket(String eventName, long remainingMs) {
        this.eventName = eventName;
        this.remainingMs = remainingMs;
    }

    public static void encode(EventTimerPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.eventName);
        buf.writeLong(packet.remainingMs);
    }

    public static EventTimerPacket decode(FriendlyByteBuf buf) {
        return new EventTimerPacket(buf.readUtf(), buf.readLong());
    }

    public static void handle(EventTimerPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> EventTimerHud.setTimer(packet.eventName, packet.remainingMs));
        ctx.get().setPacketHandled(true);
    }
}
