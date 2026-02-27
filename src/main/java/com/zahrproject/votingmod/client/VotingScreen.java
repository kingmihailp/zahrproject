package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.VotePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client-side voting screen.
 * Shown to all players when a new vote starts.
 * Closes automatically after the vote timer expires or when the player votes.
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

    // Button references for highlighting
    private Button btnA;
    private Button btnB;

    // Colors
    private static final int COLOR_TITLE = 0xFFFFD700;        // Gold
    private static final int COLOR_SUBTITLE = 0xFFFFFFFF;     // White
    private static final int COLOR_TIMER_OK = 0xFF00FF00;     // Green
    private static final int COLOR_TIMER_LOW = 0xFFFF4444;    // Red
    private static final int COLOR_WIN = 0xFF00FF00;          // Green winner
    private static final int COLOR_LOSE = 0xFFFF4444;         // Red loser

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

        int btnWidth = Math.min(200, (this.width / 2) - 20);
        int btnHeight = 30;
        int gap = 20;

        // Option A button (left side)
        btnA = Button.builder(Component.literal(wrapText(optionA, 24)), btn -> castVote(0))
                .pos(centerX - btnWidth - gap / 2, centerY + 10)
                .size(btnWidth, btnHeight)
                .build();
        this.addRenderableWidget(btnA);

        // Option B button (right side)
        btnB = Button.builder(Component.literal(wrapText(optionB, 24)), btn -> castVote(1))
                .pos(centerX + gap / 2, centerY + 10)
                .size(btnWidth, btnHeight)
                .build();
        this.addRenderableWidget(btnB);
    }

    private void castVote(int choice) {
        if (hasVoted || showingResult) return;
        hasVoted = true;
        myVote = choice;

        // Disable both buttons after voting
        btnA.active = false;
        btnB.active = false;

        // Send vote to server
        ModNetwork.CHANNEL.sendToServer(new VotePacket(choice));
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        long elapsed = now - openTimeMs;
        remainingMs = Math.max(0, durationSeconds * 1000L - elapsed);

        if (showingResult) {
            long resultElapsed = now - resultShowStartMs;
            if (resultElapsed >= RESULT_DISPLAY_MS) {
                // Close screen after showing result
                this.onClose();
            }
        } else if (remainingMs <= 0 && !hasVoted) {
            // Timer expired without voting — close
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background overlay
        renderBackground(graphics);
        renderVotingPanel(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderVotingPanel(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Panel background
        int panelW = this.width - 60;
        int panelH = 160;
        int panelX = 30;
        int panelY = centerY - 90;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC000000);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFFFFD700);
        graphics.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, 0xFFFFD700);
        graphics.fill(panelX, panelY, panelX + 2, panelY + panelH, 0xFFFFD700);
        graphics.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, 0xFFFFD700);

        if (showingResult) {
            renderResultPanel(graphics, centerX, centerY);
        } else {
            renderVotingContent(graphics, centerX, panelY);
        }
    }

    private void renderVotingContent(GuiGraphics graphics, int centerX, int panelY) {
        // Title
        String title = "ГОЛОСОВАНИЕ";
        graphics.drawCenteredString(font, title, centerX, panelY + 10, COLOR_TITLE);

        // Separator line text
        graphics.drawCenteredString(font, "Выберите событие:", centerX, panelY + 28, COLOR_SUBTITLE);

        // Timer
        long secs = remainingMs / 1000;
        int timerColor = secs > 10 ? COLOR_TIMER_OK : COLOR_TIMER_LOW;
        String timerText = "Осталось: " + secs + " сек.";
        graphics.drawCenteredString(font, timerText, centerX, panelY + 44, timerColor);

        if (hasVoted) {
            String votedMsg = myVote == 0
                    ? "§aВы проголосовали за вариант A!"
                    : "§aВы проголосовали за вариант B!";
            graphics.drawCenteredString(font, votedMsg, centerX, panelY + 62, 0xFFFFFFFF);
            graphics.drawCenteredString(font, "Ожидание результатов...", centerX, panelY + 76, 0xFFAAAAAA);
        }

        // Option labels above buttons
        int btnWidth = Math.min(200, (this.width / 2) - 20);
        int gap = 20;
        int btnY = (this.height / 2) + 10;

        // Option A label
        graphics.drawCenteredString(font, "§eВариант A:", centerX - btnWidth / 2 - gap / 2, btnY - 14, 0xFFFFFFFF);
        // Option B label
        graphics.drawCenteredString(font, "§eВариант B:", centerX + btnWidth / 2 + gap / 2, btnY - 14, 0xFFFFFFFF);
    }

    private void renderResultPanel(GuiGraphics graphics, int centerX, int centerY) {
        int panelY = centerY - 90;

        graphics.drawCenteredString(font, "§6РЕЗУЛЬТАТЫ ГОЛОСОВАНИЯ", centerX, panelY + 10, COLOR_TITLE);

        // Winner announcement
        graphics.drawCenteredString(font, "§fПобедил вариант: §a" + (resultWinner == 0 ? "A" : "B"),
                centerX, panelY + 30, 0xFFFFFFFF);

        // Winning action text
        graphics.drawCenteredString(font, "§e" + resultText, centerX, panelY + 46, 0xFFFFFF00);

        // Vote counts
        String optALabel = (resultWinner == 0 ? "§a" : "§c") + "Вариант A: " + resultVotesA + " гол.";
        String optBLabel = (resultWinner == 1 ? "§a" : "§c") + "Вариант B: " + resultVotesB + " гол.";
        graphics.drawCenteredString(font, optALabel, centerX - 80, panelY + 68, 0xFFFFFFFF);
        graphics.drawCenteredString(font, optBLabel, centerX + 80, panelY + 68, 0xFFFFFFFF);

        // Progress bar for votes
        int totalVotes = resultVotesA + resultVotesB;
        if (totalVotes > 0) {
            int barW = 260;
            int barH = 10;
            int barX = centerX - barW / 2;
            int barY = panelY + 82;

            int aWidth = (int) ((float) resultVotesA / totalVotes * barW);

            graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF444444);
            if (aWidth > 0) graphics.fill(barX, barY, barX + aWidth, barY + barH, 0xFF00CC00);
            if (aWidth < barW) graphics.fill(barX + aWidth, barY, barX + barW, barY + barH, 0xFFCC0000);
            graphics.fill(barX, barY, barX + barW, barY + 1, 0xFFFFFFFF);
            graphics.fill(barX, barY + barH - 1, barX + barW, barY + barH, 0xFFFFFFFF);
        }

        // Closing countdown
        long closeIn = Math.max(0, RESULT_DISPLAY_MS - (System.currentTimeMillis() - resultShowStartMs));
        graphics.drawCenteredString(font, "§7Закрытие через " + (closeIn / 1000 + 1) + " сек...",
                centerX, panelY + 100, 0xFFAAAAAA);
    }

    /**
     * Called by VoteResultPacket handler to display the result.
     */
    public void showResult(int winner, String winnerText, int votesA, int votesB) {
        this.showingResult = true;
        this.resultWinner = winner;
        this.resultText = winnerText;
        this.resultVotesA = votesA;
        this.resultVotesB = votesB;
        this.resultShowStartMs = System.currentTimeMillis();

        // Disable vote buttons
        if (btnA != null) btnA.active = false;
        if (btnB != null) btnB.active = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * Wraps text to a given max length (rough word wrap for button labels).
     */
    private String wrapText(String text, int maxLineLength) {
        if (text.length() <= maxLineLength) return text;
        // Just truncate with ellipsis for button labels (full text shown in tooltip)
        return text.substring(0, maxLineLength - 3) + "...";
    }
}
