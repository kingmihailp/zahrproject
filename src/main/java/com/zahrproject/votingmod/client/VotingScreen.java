package com.zahrproject.votingmod.client;

import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.VotePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client-side voting screen.
 * Shows two large MultilineButton widgets so the full option text is always visible.
 * Closes automatically after the vote timer expires or after showing the result.
 */
public class VotingScreen extends Screen {

    private final String optionA;
    private final String optionB;
    private final long durationSeconds;

    // Countdown
    private long openTimeMs;
    private long remainingMs;

    // Vote state
    private boolean hasVoted = false;
    private int myVote = -1;

    // Result state
    private boolean showingResult = false;
    private int resultWinner = -1;
    private String resultText = "";
    private int resultVotesA = 0;
    private int resultVotesB = 0;
    private long resultShowStartMs = 0;
    private static final long RESULT_DISPLAY_MS = 5000;

    // Buttons
    private MultilineButton btnA;
    private MultilineButton btnB;

    // Panel layout — computed in init(), used in render()
    private int panelX, panelY, panelW, panelH;
    private int btnY, btnW, btnH;

    // Colors
    private static final int COLOR_TITLE     = 0xFFFFD700; // Gold
    private static final int COLOR_WHITE     = 0xFFFFFFFF;
    private static final int COLOR_TIMER_OK  = 0xFF00FF00; // Green
    private static final int COLOR_TIMER_LOW = 0xFFFF4444; // Red

    public VotingScreen(String optionA, String optionB, long durationSeconds) {
        super(Component.literal("Голосование"));
        this.optionA = optionA;
        this.optionB = optionB;
        this.durationSeconds = durationSeconds;
    }

    @Override
    protected void init() {
        this.openTimeMs = System.currentTimeMillis();
        this.remainingMs = durationSeconds * 1000L;

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Panel: max 720px wide, centred horizontally, 210px tall
        panelW = Math.min(this.width - 40, 720);
        panelH = 210;
        panelX = (this.width - panelW) / 2;
        panelY = centerY - panelH / 2;

        // Buttons: fill panel width minus side margins and a gap between them
        int sideMargin = 12;
        int gap        = 10;
        btnW = (panelW - 2 * sideMargin - gap) / 2;
        btnH = 85;
        btnY = panelY + panelH - btnH - 12; // bottom-aligned inside panel

        btnA = new MultilineButton(
                panelX + sideMargin, btnY, btnW, btnH,
                optionA, this.font, btn -> castVote(0));
        addRenderableWidget(btnA);

        btnB = new MultilineButton(
                panelX + sideMargin + btnW + gap, btnY, btnW, btnH,
                optionB, this.font, btn -> castVote(1));
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

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        long now     = System.currentTimeMillis();
        long elapsed = now - openTimeMs;
        remainingMs  = Math.max(0, durationSeconds * 1000L - elapsed);

        if (showingResult) {
            if (now - resultShowStartMs >= RESULT_DISPLAY_MS) {
                onClose();
            }
        } else if (remainingMs <= 0 && !hasVoted) {
            onClose();
        }
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics);
        if (showingResult) {
            drawResultContent(graphics);
        } else {
            drawVotingHeader(graphics);
        }
        // Renders the MultilineButton widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Dark panel with gold border. */
    private void drawPanel(GuiGraphics g) {
        int x = panelX, y = panelY, w = panelW, h = panelH;
        g.fill(x, y, x + w, y + h, 0xCC000000);
        // Gold border (2px)
        g.fill(x,         y,         x + w,     y + 2,     0xFFFFD700);
        g.fill(x,         y + h - 2, x + w,     y + h,     0xFFFFD700);
        g.fill(x,         y,         x + 2,     y + h,     0xFFFFD700);
        g.fill(x + w - 2, y,         x + w,     y + h,     0xFFFFD700);
    }

    /** Header drawn when a vote is active. */
    private void drawVotingHeader(GuiGraphics g) {
        int cx = this.width / 2;

        // Title
        g.drawCenteredString(font, "ГОЛОСОВАНИЕ", cx, panelY + 9, COLOR_TITLE);

        // Subtitle
        g.drawCenteredString(font, "Выберите событие:", cx, panelY + 24, COLOR_WHITE);

        // Timer
        long secs = remainingMs / 1000;
        int timerColor = secs > 10 ? COLOR_TIMER_OK : COLOR_TIMER_LOW;
        g.drawCenteredString(font, "Осталось: " + secs + " сек.", cx, panelY + 39, timerColor);

        // Option labels above buttons (yellow)
        int labelY = btnY - 13;
        int aCenterX = panelX + 12 + btnW / 2;
        int bCenterX = panelX + 12 + btnW + 10 + btnW / 2;
        g.drawCenteredString(font, "§eВариант A", aCenterX, labelY, COLOR_WHITE);
        g.drawCenteredString(font, "§eВариант B", bCenterX, labelY, COLOR_WHITE);

        // Voted message
        if (hasVoted) {
            String msg = myVote == 0
                    ? "§aВы проголосовали за вариант A!"
                    : "§aВы проголосовали за вариант B!";
            g.drawCenteredString(font, msg,                        cx, panelY + 56, COLOR_WHITE);
            g.drawCenteredString(font, "Ожидание результатов...", cx, panelY + 69, 0xFFAAAAAA);
        }
    }

    /** Content drawn while showing the vote result. */
    private void drawResultContent(GuiGraphics g) {
        int cx = this.width / 2;

        g.drawCenteredString(font, "§6РЕЗУЛЬТАТЫ ГОЛОСОВАНИЯ", cx, panelY + 9,  COLOR_TITLE);
        g.drawCenteredString(font,
                "§fПобедил вариант: §a" + (resultWinner == 0 ? "A" : "B"),
                cx, panelY + 26, COLOR_WHITE);

        // Winning text (truncated if too wide to fit)
        String displayText = resultText.length() > 60
                ? resultText.substring(0, 57) + "..."
                : resultText;
        g.drawCenteredString(font, "§e" + displayText, cx, panelY + 42, 0xFFFFFF00);

        // Vote count labels
        int aCenterX = panelX + 12 + btnW / 2;
        int bCenterX = panelX + 12 + btnW + 10 + btnW / 2;
        String aLabel = (resultWinner == 0 ? "§a" : "§c") + "A: " + resultVotesA + " гол.";
        String bLabel = (resultWinner == 1 ? "§a" : "§c") + "B: " + resultVotesB + " гол.";
        g.drawCenteredString(font, aLabel, aCenterX, panelY + 62, COLOR_WHITE);
        g.drawCenteredString(font, bLabel, bCenterX, panelY + 62, COLOR_WHITE);

        // Progress bar
        int totalVotes = resultVotesA + resultVotesB;
        if (totalVotes > 0) {
            int barW  = panelW - 40;
            int barH  = 12;
            int barX  = panelX + 20;
            int barY  = panelY + 78;
            int aFill = (int) ((float) resultVotesA / totalVotes * barW);

            g.fill(barX,          barY, barX + barW, barY + barH, 0xFF333333);
            if (aFill > 0)        g.fill(barX,          barY, barX + aFill,  barY + barH, 0xFF00BB00);
            if (aFill < barW)     g.fill(barX + aFill,  barY, barX + barW,   barY + barH, 0xFFBB0000);
            // border
            g.fill(barX, barY,            barX + barW, barY + 1,      0xFFFFFFFF);
            g.fill(barX, barY + barH - 1, barX + barW, barY + barH,   0xFFFFFFFF);
        }

        // "Winning" result buttons (disabled, highlighted by winner color)
        if (btnA != null) btnA.active = false;
        if (btnB != null) btnB.active = false;

        // Closing countdown
        long closeIn = Math.max(0, RESULT_DISPLAY_MS - (System.currentTimeMillis() - resultShowStartMs));
        g.drawCenteredString(font,
                "§7Закрытие через " + (closeIn / 1000 + 1) + " сек...",
                cx, panelY + panelH - 18, 0xFFAAAAAA);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Called by VoteResultPacket handler to display the result. */
    public void showResult(int winner, String winnerText, int votesA, int votesB) {
        this.showingResult    = true;
        this.resultWinner     = winner;
        this.resultText       = winnerText;
        this.resultVotesA     = votesA;
        this.resultVotesB     = votesB;
        this.resultShowStartMs = System.currentTimeMillis();
        if (btnA != null) btnA.active = false;
        if (btnB != null) btnB.active = false;
    }

    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
