package com.zahrproject.votingmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders active timed-event bars on the right-centre of the screen.
 *
 *   ┌────────────────────────────────────────┐
 *   │ Все вверх дном                    2:45 │
 *   │ ████████████████░░░░░░░░░░░░░░░░░░░░░ │
 *   └────────────────────────────────────────┘
 *   ┌────────────────────────────────────────┐
 *   │ Прикосновение Мидаса              1:30 │
 *   │ ███████████████████████░░░░░░░░░░░░░░ │
 *   └────────────────────────────────────────┘
 *
 * Bars stack vertically; each new event is appended below the previous ones.
 * A bar disappears automatically when its timer expires.
 */
@OnlyIn(Dist.CLIENT)
public class EventTimerHud {

    // Map<eventName, long[]{durationMs, expiryMs}> — insertion-ordered
    private static final Map<String, long[]> activeTimers = new LinkedHashMap<>();

    // Widget dimensions (GUI-scaled pixels)
    private static final int BG_W   = 160;
    private static final int BG_H   = 26;
    private static final int GAP    = 4;   // vertical gap between bars
    private static final int PAD    = 4;   // inner padding on all sides
    private static final int BAR_H  = 7;   // height of the depleting fill bar
    private static final int MARGIN = 5;   // distance from screen edges

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called by {@link com.zahrproject.votingmod.network.EventTimerPacket} on the client thread.
     * remainingMs > 0 → start/update; remainingMs <= 0 → remove.
     */
    public static void setTimer(String name, long remainingMs) {
        if (remainingMs <= 0) {
            activeTimers.remove(name);
        } else {
            activeTimers.put(name, new long[]{remainingMs, System.currentTimeMillis() + remainingMs});
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (activeTimers.isEmpty()) return;

        long now = System.currentTimeMillis();
        activeTimers.entrySet().removeIf(e -> e.getValue()[1] <= now);
        if (activeTimers.isEmpty()) return;

        Minecraft mc     = Minecraft.getInstance();
        Font     font    = mc.font;
        int      screenW = mc.getWindow().getGuiScaledWidth();
        int      screenH = mc.getWindow().getGuiScaledHeight();

        int count  = activeTimers.size();
        int totalH = count * BG_H + (count - 1) * GAP;
        int startX = screenW - BG_W - MARGIN;
        int startY = (screenH - totalH) / 2;

        GuiGraphics g   = event.getGuiGraphics();
        int         idx = 0;

        for (Map.Entry<String, long[]> entry : activeTimers.entrySet()) {
            String name      = entry.getKey();
            long[] data      = entry.getValue();   // [durationMs, expiryMs]
            long   durationMs = data[0];
            long   expiryMs  = data[1];
            long   remaining = Math.max(0, expiryMs - now);
            float  fraction  = durationMs > 0 ? (float) remaining / durationMs : 0f;

            int x = startX;
            int y = startY + idx * (BG_H + GAP);
            int eventColor = colorForEvent(name);

            // ── Background ───────────────────────────────────────────────────
            g.fill(x, y, x + BG_W, y + BG_H, 0xCC000000);

            // ── Coloured border (1 px) ───────────────────────────────────────
            int border = withAlpha(eventColor, 0xCC);
            g.fill(x,           y,           x + BG_W,     y + 1,      border); // top
            g.fill(x,           y + BG_H - 1, x + BG_W,   y + BG_H,   border); // bottom
            g.fill(x,           y,           x + 1,        y + BG_H,   border); // left
            g.fill(x + BG_W - 1, y,          x + BG_W,    y + BG_H,   border); // right

            // ── Text row: name (left) | time (right) ────────────────────────
            long   secs    = remaining / 1000;
            String timeStr = String.format("%d:%02d", secs / 60, secs % 60);
            int    timeW   = font.width(timeStr);

            g.drawString(font, name,    x + PAD,                  y + PAD, 0xFFEEEEEE, false);
            g.drawString(font, timeStr, x + BG_W - timeW - PAD,   y + PAD, eventColor, false);

            // ── Depleting bar ────────────────────────────────────────────────
            int barX      = x + PAD;
            int barY      = y + PAD + 9; // 9 = font height (8) + 1 px gap
            int barInnerW = BG_W - PAD * 2;
            int filled    = Math.round(fraction * barInnerW);

            g.fill(barX,          barY, barX + barInnerW, barY + BAR_H, 0xFF333333);
            if (filled > 0) {
                g.fill(barX, barY, barX + filled, barY + BAR_H, eventColor);
            }

            idx++;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns a colour for the named event, falling back to green. */
    private static int colorForEvent(String name) {
        if (name.contains("дном"))    return 0xFFFF6060; // Все вверх дном      → red
        if (name.contains("Мидас"))   return 0xFFFFD700; // Мидас               → gold
        if (name.contains("детство")) return 0xFF5BC0FF; // Обратно в детство   → light blue
        return 0xFF88FF88;                                // fallback             → green
    }

    /** Replaces the alpha byte of a fully-opaque ARGB colour. */
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
