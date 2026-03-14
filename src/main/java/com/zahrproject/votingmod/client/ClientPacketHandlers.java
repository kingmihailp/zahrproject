package com.zahrproject.votingmod.client;

import com.zahrproject.votingmod.events.ChildEventManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Set;
import java.util.UUID;

/**
 * All packet handling logic that requires client-only classes lives here.
 * This class is annotated {@link OnlyIn}(CLIENT) so the server never loads it.
 * Packet classes must NEVER import client classes directly – they route through
 * this class via {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.xxx())}.
 */
@OnlyIn(Dist.CLIENT)
public class ClientPacketHandlers {

    public static void handleOpenVotingScreen(String optionA, String optionB, long durationSeconds) {
        Minecraft.getInstance().setScreen(new VotingScreen(optionA, optionB, durationSeconds));
    }

    public static void handleVoteResult(int winnerOption, int votesA, int votesB,
                                        String optionA, String optionB) {
        VoteResultOverlay.show(winnerOption, votesA, votesB, optionA, optionB);
    }

    /** Called after {@code ChildEventManager.setChildPlayers} has already been applied. */
    public static void refreshChildDimensions() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                player.refreshDimensions();
            }
        }
    }

    public static void handleEventTimer(String eventName, long remainingMs, long totalDurationMs) {
        EventTimerHud.setTimer(eventName, remainingMs, totalDurationMs);
    }

    public static void handleFlipScreen(boolean flipped) {
        ScreenFlipHandler.setFlipped(flipped);
    }

    public static void handleFlipModel(boolean active) {
        PlayerFlipRenderHandler.setFlipped(active);
    }

    public static void handleInvertColors(boolean active) {
        InvertColorsHandler.setActive(active);
    }

    public static void handleRandomTexture(boolean active) {
        RandomTextureHandler.setActive(active);
    }

    public static void handleBigHead(boolean active) {
        BigHeadRenderHandler.setActive(active);
    }
}
