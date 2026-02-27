package com.zahrproject.votingmod.client;

import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.VotePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Voting screen: two randomly-picked events side by side.
 * The player votes for A or B; the event with more votes happens.
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  ★  ГОЛОСОВАНИЕ  ★          Осталось: 28 сек.           │
 *  │  Проголосуйте за одно из двух событий:                  │
 *  │──────────────────────────────────────────────────────────│
 *  │  Вариант A                    Вариант B                  │
 *  │  [ Призвать 5 зомби          [ Установить погоду         │
 *  │    рядом с игроками ]          на грозу        ]        │
 *  └──────────────────────────────────────────────────────────┘
 */
public class VotingScreen extends Screen {

    // ── Data ──────────────────────────────────────────────────────────────────
    private final String optionA;
    private final String optionB;
    private final long   durationSeconds;

    // Countdown
    private long openTimeMs;
    private long remainingMs;

    // Vote state — -1=not voted, 0=A, 1=B
    private int  myVote    = -1;
    private boolean hasVoted = false;

    // Result state
    private boolean showingResult  = false;
    private int     resultWinner   = -1; // 0=A won, 1=B won
    private int     resultVotesA   = 0;
    private int     resultVotesB   = 0;
    private long    resultShowStartMs = 0;
    private static final long RESULT_DISPLAY_MS = 5500;

    // Buttons
    private MultilineButton btnA;
    private MultilineButton btnB;

    // Panel layout — computed in init()
    private int panelX, panelY, panelW, panelH;
    private int btnW, btnH, btnAX, btnBX, btnY;

    // Colors
    private static final int COLOR_GOLD      = 0xFFFFD700;
    private static final int COLOR_WHITE     = 0xFFFFFFFF;
    private static final int COLOR_GRAY      = 0xFFAAAAAA;
    private static final int COLOR_TIMER_OK  = 0xFF44FF44;
    private static final int COLOR_TIMER_LOW = 0xFFFF4444;

    // Accent for option A (blue) and B (amber)
    private static final int ACCENT_A = MultilineButton.ACCENT_BLUE;
    private static final int ACCENT_B = 0xDD8800; // amber

    // ── Constructor ───────────────────────────────────────────────────────────

    public VotingScreen(String optionA, String optionB, long durationSeconds) {
        super(Component.literal("Голосование"));
        this.optionA         = optionA;
        this.optionB         = optionB;
        this.durationSeconds = durationSeconds;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        this.openTimeMs  = System.currentTimeMillis();
        this.remainingMs = durationSeconds * 1000L;

        int centerY = this.height / 2;

        panelW = Math.min(this.width - 40, 720);
        panelH = 210;
        panelX = (this.width - panelW) / 2;
        panelY = centerY - panelH / 2;

        int margin = 12;
        int gap    = 10;
        btnH  = 90;
        btnW  = (panelW - 2 * margin - gap) / 2;
        btnY  = panelY + panelH - btnH - margin;
        btnAX = panelX + margin;
        btnBX = panelX + margin + btnW + gap;

        btnA = new MultilineButton(btnAX, btnY, btnW, btnH,
                optionA, this.font, ACCENT_A, btn -> castVote(0));
        addRenderableWidget(btnA);

        btnB = new MultilineButton(btnBX, btnY, btnW, btnH,
                optionB, this.font, ACCENT_B, btn -> castVote(1));
        addRenderableWidget(btnB);
    }

    private void castVote(int choice) {
        if (hasVoted || showingResult) return;
        hasVoted = true;
        myVote   = choice;
        btnA.active = false;
        btnB.active = false;
        ModNetwork.CHANNEL.sendToServer(new VotePacket(choice));
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
        else               drawVoteHeader(g);
        super.render(g, mouseX, mouseY, partialTick); // draws buttons
    }

    private void drawPanel(GuiGraphics g) {
        int x = panelX, y = panelY, w = panelW, h = panelH;
        g.fill(x, y, x + w, y + h, 0xCC000000);
        g.fill(x,         y,         x + w,     y + 2,     COLOR_GOLD);
        g.fill(x,         y + h - 2, x + w,     y + h,     COLOR_GOLD);
        g.fill(x,         y,         x + 2,     y + h,     COLOR_GOLD);
        g.fill(x + w - 2, y,         x + w,     y + h,     COLOR_GOLD);
    }

    private void drawVoteHeader(GuiGraphics g) {
        int cx = this.width / 2;

        g.drawCenteredString(font, "★  ГОЛОСОВАНИЕ  ★", cx, panelY + 9, COLOR_GOLD);

        long secs     = remainingMs / 1000;
        int  timerCol = secs > 10 ? COLOR_TIMER_OK : COLOR_TIMER_LOW;
        g.drawCenteredString(font, "Осталось: " + secs + " сек.", cx, panelY + 23, timerCol);

        g.drawCenteredString(font, "Проголосуйте за одно из двух событий:",
                cx, panelY + 37, COLOR_GRAY);

        // Thin separator
        g.fill(panelX + 10, panelY + 49, panelX + panelW - 10, panelY + 50, 0x88FFD700);

        // Option labels above buttons
        int labelY  = btnY - 13;
        int aCenterX = btnAX + btnW / 2;
        int bCenterX = btnBX + btnW / 2;
        g.drawCenteredString(font, "§eВариант A", aCenterX, labelY, COLOR_WHITE);
        g.drawCenteredString(font, "§eВариант B", bCenterX, labelY, COLOR_WHITE);

        // Voted confirmation
        if (hasVoted) {
            String msg = "§aВы проголосовали за вариант " + (myVote == 0 ? "A" : "B") + "!";
            g.drawCenteredString(font, msg, cx, panelY + 58, COLOR_WHITE);
            g.drawCenteredString(font, "Ожидание результатов...", cx, panelY + 70, COLOR_GRAY);
        }
    }

    private void drawResultContent(GuiGraphics g) {
        int cx = this.width / 2;

        // Winner header
        String winner = resultWinner == 0 ? "A" : "B";
        g.drawCenteredString(font, "§6★  Победил вариант " + winner + "!  ★",
                cx, panelY + 9, COLOR_GOLD);

        // Thin separator
        g.fill(panelX + 10, panelY + 22, panelX + panelW - 10, panelY + 23, 0x88FFD700);

        // Winning event description (centered)
        String winDesc = resultWinner == 0 ? optionA : optionB;
        g.drawCenteredString(font, "§e" + winDesc, cx, panelY + 30, 0xFFFFFF00);

        // Vote progress bar
        int total = resultVotesA + resultVotesB;
        if (total > 0) {
            int barW  = panelW - 40;
            int barH  = 12;
            int barX  = panelX + 20;
            int barY  = btnY - 28;
            int aFill = (int)((float) resultVotesA / total * barW);

            g.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
            if (aFill > 0)     g.fill(barX,         barY, barX + aFill, barY + barH, 0xFF2288DD); // A = blue
            if (aFill < barW)  g.fill(barX + aFill, barY, barX + barW,  barY + barH, 0xFFDD8800); // B = amber
            g.fill(barX, barY,            barX + barW, barY + 1,          COLOR_WHITE);
            g.fill(barX, barY + barH - 1, barX + barW, barY + barH,       COLOR_WHITE);

            // Labels
            String aLabel = (resultWinner == 0 ? "§b" : "§7") + "A: " + resultVotesA + " гол.";
            String bLabel = (resultWinner == 1 ? "§6" : "§7") + "B: " + resultVotesB + " гол.";
            int bLabelW   = font.width(bLabel);
            g.drawString(font, aLabel, barX + 2,                 barY - 11, COLOR_WHITE, false);
            g.drawString(font, bLabel, barX + barW - bLabelW - 2, barY - 11, COLOR_WHITE, false);
        }

        // Countdown
        long closeIn = Math.max(0, RESULT_DISPLAY_MS - (System.currentTimeMillis() - resultShowStartMs));
        g.drawCenteredString(font,
                "§7Закрытие через " + (closeIn / 1000 + 1) + " сек...",
                cx, panelY + panelH - 18, COLOR_GRAY);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called by VoteResultPacket handler. */
    public void showResult(int winner, int votesA, int votesB) {
        this.showingResult    = true;
        this.resultWinner     = winner;
        this.resultVotesA     = votesA;
        this.resultVotesB     = votesB;
        this.resultShowStartMs = System.currentTimeMillis();
        if (btnA != null) btnA.active = false;
        if (btnB != null) btnB.active = false;
    }

    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
