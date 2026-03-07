package com.zahrproject.votingmod.network;

import com.zahrproject.votingmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet.
 * Starts, updates or removes a named event timer on the HUD.
 *   remainingMs > 0  → show/update timer for this event
 *   remainingMs <= 0 → remove timer for this event
 */
public class EventTimerPacket {

    private final String eventName;
    private final long   remainingMs;
    private final long   totalDurationMs;

    public EventTimerPacket(String eventName, long remainingMs, long totalDurationMs) {
        this.eventName       = eventName;
        this.remainingMs     = remainingMs;
        this.totalDurationMs = totalDurationMs;
    }

    public static void encode(EventTimerPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.eventName);
        buf.writeLong(packet.remainingMs);
        buf.writeLong(packet.totalDurationMs);
    }

    public static EventTimerPacket decode(FriendlyByteBuf buf) {
        return new EventTimerPacket(buf.readUtf(), buf.readLong(), buf.readLong());
    }

    public static void handle(EventTimerPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandlers.handleEventTimer(
                                packet.eventName, packet.remainingMs, packet.totalDurationMs)));
        ctx.get().setPacketHandled(true);
    }
}
