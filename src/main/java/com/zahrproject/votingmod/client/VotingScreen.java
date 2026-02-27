package com.zahrproject.votingmod.client;

import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.VotePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Voting screen shown to every player at the start of a vote.
 *
 * Layout:
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  ★ ГОЛОСОВАНИЕ ★                  Осталось: 28 сек.    │
 *   │─────────────────────────────────────────────────────────│
 *   │                                                         │
 *   │     Призвать 5 зомби рядом с каждым игроком?           │
 *   │                                                         │
 *   │─────────────────────────────────────────────────────────│
 *   │  [  ✔  ДА — пусть случится!  ]       [  ✘  НЕТ  ]    │
 *   └─────────────────────────────────────────────────────────┘
 */
public class VotingScreen extends Screen {

    // ── Data ──────────────────────────────────────────────────────────────────
    private final String eventDescription;
    private final long   durationSeconds;

    // Countdown
    private long openTimeMs;
    private long remainingMs;

    // Vote state
    private boolean hasVoted = false;
    private boolean votedYes = false;

    // Result state
    private boolean showingResult    = false;
    private boolean resultYesWon     = false;
    private int     resultYesCount   = 0;
    private int     resultNoCount    = 0;
    private long    resultShowStartMs = 0;
    private static final long RESULT_DISPLAY_MS = 5500;

    // Buttons
    private MultilineButton btnYes;
    private MultilineButton btnNo;

    // Panel layout — set in init()
    private int panelX, panelY, panelW, panelH;
    private int btnY, btnH, btnYesW, btnNoW;

    // Description word-wrap — computed in init()
    private List<String> descLines;

    // Colors
    private static final int COLOR_GOLD      = 0xFFFFD700;
    private static final int COLOR_WHITE     = 0xFFFFFFFF;
    private static final int COLOR_GRAY      = 0xFFAAAAAA;
    private static final int COLOR_TIMER_OK  = 0xFF44FF44;
    private static final int COLOR_TIMER_LOW = 0xFFFF4444;

    // ── Constructor ───────────────────────────────────────────────────────────

    public VotingScreen(String eventDescription, long durationSeconds) {
        super(Component.literal("Голосование"));
        this.eventDescription = eventDescription;
        this.durationSeconds  = durationSeconds;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        this.openTimeMs  = System.currentTimeMillis();
        this.remainingMs = durationSeconds * 1000L;

        int centerY = this.height / 2;

        // Panel dimensions
        panelW = Math.min(this.width - 40, 700);
        panelH = 220;
        panelX = (this.width - panelW) / 2;
        panelY = centerY - panelH / 2;

        // Pre-compute word-wrapped description
        descLines = wordWrap(eventDescription, panelW - 60);

        // Button layout: YES is wide, NO is compact on the right
        int gap    = 10;
        int margin = 12;
        btnH   = 72;
        btnNoW  = 90;
        btnYesW = panelW - 2 * margin - gap - btnNoW;
        btnY   = panelY + panelH - btnH - margin;

        btnYes = new MultilineButton(
                panelX + margin, btnY,
                btnYesW, btnH,
                "✔  ДА — пусть случится!",
                this.font, MultilineButton.ACCENT_GREEN,
                btn -> castVote(true));
        addRenderableWidget(btnYes);

        btnNo = new MultilineButton(
                panelX + margin + btnYesW + gap, btnY,
                btnNoW, btnH,
                "✘  НЕТ",
                this.font, MultilineButton.ACCENT_RED,
                btn -> castVote(false));
        addRenderableWidget(btnNo);
    }

    private void castVote(boolean yes) {
        if (hasVoted || showingResult) return;
        hasVoted = true;
        votedYes = yes;
        btnYes.active = false;
        btnNo.active  = false;
        ModNetwork.CHANNEL.sendToServer(new VotePacket(yes ? 0 : 1));
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        long now    = System.currentTimeMillis();
        remainingMs = Math.max(0, durationSeconds * 1000L - (now - openTimeMs));

        if (showingResult) {
            if (now - resultShowStartMs >= RESULT_DISPLAY_MS) onClose();
        } else if (remainingMs <= 0 && !hasVoted) {
            onClose();
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawPanel(g);
        if (showingResult) drawResultContent(g);
        else               drawVoteContent(g);
        super.render(g, mouseX, mouseY, partialTick); // draws buttons
    }

    /** Dark panel with 2px gold border. */
    private void drawPanel(GuiGraphics g) {
        int x = panelX, y = panelY, w = panelW, h = panelH;
        g.fill(x, y, x + w, y + h, 0xCC000000);
        g.fill(x,         y,         x + w,     y + 2,     COLOR_GOLD);
        g.fill(x,         y + h - 2, x + w,     y + h,     COLOR_GOLD);
        g.fill(x,         y,         x + 2,     y + h,     COLOR_GOLD);
        g.fill(x + w - 2, y,         x + w,     y + h,     COLOR_GOLD);
    }

    /** Active voting view: title, timer, description, optional voted message. */
    private void drawVoteContent(GuiGraphics g) {
        int cx = this.width / 2;

        // Title
        g.drawCenteredString(font, "★  ГОЛОСОВАНИЕ  ★", cx, panelY + 9, COLOR_GOLD);

        // Timer
        long secs     = remainingMs / 1000;
        int  timerCol = secs > 10 ? COLOR_TIMER_OK : COLOR_TIMER_LOW;
        g.drawCenteredString(font, "Осталось: " + secs + " сек.", cx, panelY + 24, timerCol);

        // Separator line
        g.fill(panelX + 10, panelY + 36, panelX + panelW - 10, panelY + 37, 0x88FFD700);

        // Event description — vertically centred in the space above buttons
        int descAreaTop  = panelY + 43;
        int descAreaBot  = btnY - 14;
        int lineH        = font.lineHeight + 4;
        int totalDescH   = descLines.size() * lineH - 4;
        int descStartY   = descAreaTop + Math.max(0, (descAreaBot - descAreaTop - totalDescH) / 2);

        for (String line : descLines) {
            g.drawCenteredString(font, line, cx, descStartY, COLOR_WHITE);
            descStartY += lineH;
        }

        // Voted confirmation
        if (hasVoted) {
            String msg = votedYes ? "§aВы проголосовали ДА!" : "§cВы проголосовали НЕТ!";
            g.drawCenteredString(font, msg, cx, btnY - 12, COLOR_WHITE);
        }
    }

    /** Result view shown after the vote ends. */
    private void drawResultContent(GuiGraphics g) {
        int cx = this.width / 2;

        // Result header
        String header = resultYesWon ? "§a✔  СОБЫТИЕ ПРОИЗОШЛО!" : "§c✘  СОБЫТИЕ ОТМЕНЕНО!";
        g.drawCenteredString(font, header, cx, panelY + 9, COLOR_GOLD);

        // Separator
        g.fill(panelX + 10, panelY + 22, panelX + panelW - 10, panelY + 23, 0x88FFD700);

        // Event description (dimmed)
        int lineH      = font.lineHeight + 4;
        int descStartY = panelY + 30;
        for (String line : descLines) {
            g.drawCenteredString(font, "§7" + line, cx, descStartY, COLOR_GRAY);
            descStartY += lineH;
        }

        // Progress bar (YES green / NO red)
        int total = resultYesCount + resultNoCount;
        if (total > 0) {
            int barW    = panelW - 40;
            int barH    = 12;
            int barX    = panelX + 20;
            int barY    = btnY - 30;
            int yesFill = (int)((float) resultYesCount / total * barW);

            g.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
            if (yesFill > 0)    g.fill(barX,           barY, barX + yesFill, barY + barH, 0xFF22BB44);
            if (yesFill < barW) g.fill(barX + yesFill, barY, barX + barW,    barY + barH, 0xFFCC2233);
            // top/bottom border
            g.fill(barX, barY,            barX + barW, barY + 1,            0xFFFFFFFF);
            g.fill(barX, barY + barH - 1, barX + barW, barY + barH,         0xFFFFFFFF);

            // Vote count labels above bar
            String yesLabel = "§a✔ ДА: " + resultYesCount;
            String noLabel  = "§c✘ НЕТ: " + resultNoCount;
            int noLabelW    = font.width(noLabel);
            g.drawString(font, yesLabel, barX + 2,                barY - 11, COLOR_WHITE, false);
            g.drawString(font, noLabel,  barX + barW - noLabelW - 2, barY - 11, COLOR_WHITE, false);
        }

        // Countdown to close
        long closeIn = Math.max(0, RESULT_DISPLAY_MS - (System.currentTimeMillis() - resultShowStartMs));
        g.drawCenteredString(font,
                "§7Закрытие через " + (closeIn / 1000 + 1) + " сек...",
                cx, panelY + panelH - 18, COLOR_GRAY);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called by VoteResultPacket handler on the client main thread. */
    public void showResult(boolean yesWon, int yesCount, int noCount) {
        this.showingResult    = true;
        this.resultYesWon     = yesWon;
        this.resultYesCount   = yesCount;
        this.resultNoCount    = noCount;
        this.resultShowStartMs = System.currentTimeMillis();
        if (btnYes != null) btnYes.active = false;
        if (btnNo  != null) btnNo.active  = false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> wordWrap(String text, int maxPixelWidth) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else {
                String candidate = current + " " + word;
                if (font.width(candidate) <= maxPixelWidth) {
                    current.append(" ").append(word);
                } else {
                    result.add(current.toString());
                    current = new StringBuilder(word);
                }
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result.isEmpty() ? List.of(text) : result;
    }

    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
