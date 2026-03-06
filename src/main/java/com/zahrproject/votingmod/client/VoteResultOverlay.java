package com.zahrproject.votingmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Non-blocking HUD overlay that shows voting results at the top-centre of the
 * screen for {@value #DISPLAY_MS} ms after all players have voted.
 *
 * Unlike the old approach (results shown inside the modal VotingScreen), this
 * overlay does NOT block player movement — the voting screen is already closed.
 *
 *  ┌──────────────────────────────────────────────────────────────┐
 *  │  ★  Победил вариант A!  ★                                   │
 *  │  Призвать 5 зомби рядом с каждым игроком                    │
 *  │  [████████████████████░░░░░░]  A: 3 гол.      B: 1 гол.    │
 *  │  Закрытие через 4 сек...                                     │
 *  └──────────────────────────────────────────────────────────────┘
 */
@OnlyIn(Dist.CLIENT)
public class VoteResultOverlay {

    private static final long DISPLAY_MS = 5_000L;

    private static final int COLOR_GOLD  = 0xFFFFD700;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY  = 0xFFAAAAAA;

    private static volatile boolean active = false;
    private static int    winner;
    private static int    votesA;
    private static int    votesB;
    private static String optionA = "";
    private static String optionB = "";
    private static long   showStartMs;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called from {@link com.zahrproject.votingmod.network.VoteResultPacket} handler. */
    public static void show(int winner, int votesA, int votesB, String optionA, String optionB) {
        VoteResultOverlay.winner      = winner;
        VoteResultOverlay.votesA      = votesA;
        VoteResultOverlay.votesB      = votesB;
        VoteResultOverlay.optionA     = optionA;
        VoteResultOverlay.optionB     = optionB;
        VoteResultOverlay.showStartMs = System.currentTimeMillis();
        VoteResultOverlay.active      = true;
    }

    // ── Forge Events ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!active) return;

        long elapsed = System.currentTimeMillis() - showStartMs;
        if (elapsed >= DISPLAY_MS) {
            active = false;
            return;
        }

        Minecraft mc      = Minecraft.getInstance();
        GuiGraphics g     = event.getGuiGraphics();
        int screenW       = mc.getWindow().getGuiScaledWidth();

        int panelW = Math.min(screenW - 20, 440);
        int panelH = 68;
        int panelX = (screenW - panelW) / 2;
        int panelY = 8;
        int cx     = panelX + panelW / 2;

        // ── Background ────────────────────────────────────────────────────────
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC000000);

        // ── Gold border ───────────────────────────────────────────────────────
        g.fill(panelX,              panelY,              panelX + panelW, panelY + 2,             COLOR_GOLD);
        g.fill(panelX,              panelY + panelH - 2, panelX + panelW, panelY + panelH,        COLOR_GOLD);
        g.fill(panelX,              panelY,              panelX + 2,      panelY + panelH,         COLOR_GOLD);
        g.fill(panelX + panelW - 2, panelY,              panelX + panelW, panelY + panelH,         COLOR_GOLD);

        // ── Line 1: winner title ──────────────────────────────────────────────
        String winLetter = winner == 0 ? "A" : "B";
        g.drawCenteredString(mc.font, "§6★  Победил вариант " + winLetter + "!  ★",
                cx, panelY + 5, COLOR_GOLD);

        // ── Line 2: winning event name (truncated to fit) ─────────────────────
        String winName  = winner == 0 ? optionA : optionB;
        String display  = mc.font.width(winName) > panelW - 20
                ? mc.font.plainSubstrByWidth(winName, panelW - 30) + "…"
                : winName;
        g.drawCenteredString(mc.font, "§e" + display, cx, panelY + 17, 0xFFFFFF00);

        // ── Vote bar ─────────────────────────────────────────────────────────
        int total = votesA + votesB;
        if (total > 0) {
            int barW  = panelW - 40;
            int barH  = 8;
            int barX  = panelX + 20;
            int barY  = panelY + 31;
            int aFill = (int)((float) votesA / total * barW);

            g.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
            if (aFill > 0)    g.fill(barX,         barY, barX + aFill, barY + barH, 0xFF2288DD); // A = blue
            if (aFill < barW) g.fill(barX + aFill, barY, barX + barW,  barY + barH, 0xFFDD8800); // B = amber
            // Thin highlight lines
            g.fill(barX, barY,            barX + barW, barY + 1,       COLOR_WHITE);
            g.fill(barX, barY + barH - 1, barX + barW, barY + barH,    COLOR_WHITE);

            // Vote count labels beside bar
            String aLabel = (winner == 0 ? "§b" : "§7") + "A: " + votesA + " гол.";
            String bLabel = (winner == 1 ? "§6" : "§7") + "B: " + votesB + " гол.";
            g.drawString(mc.font, aLabel, barX + 2,
                    barY + barH + 3, COLOR_WHITE, false);
            g.drawString(mc.font, bLabel, barX + barW - mc.font.width(bLabel) - 2,
                    barY + barH + 3, COLOR_WHITE, false);
        }

        // ── Countdown ─────────────────────────────────────────────────────────
        long remaining = DISPLAY_MS - elapsed;
        g.drawCenteredString(mc.font,
                "§7Закрытие через " + (remaining / 1000 + 1) + " сек.",
                cx, panelY + panelH - 11, COLOR_GRAY);
    }
}
