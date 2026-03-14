package com.zahrproject.votingmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
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
 * The map is cleared on disconnect so stale entries never bleed into the next session.
 */
@OnlyIn(Dist.CLIENT)
public class EventTimerHud {

    // Map<eventName, long[]{totalDurationMs, expiryMs}> — insertion-ordered
    private static final Map<String, long[]> activeTimers = new LinkedHashMap<>();

    // Widget dimensions (GUI-scaled pixels)
    private static final int BG_W   = 160;
    private static final int BG_H   = 26;
    private static final int GAP    = 4;   // vertical gap between bars
    private static final int PAD    = 4;   // inner padding on all sides
    private static final int BAR_H  = 7;   // height of the depleting fill bar
    private static final int MARGIN = 5;   // distance from screen edges

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns true if the named event timer is currently active on this client. */
    public static boolean isTimerActive(String name) {
        long[] data = activeTimers.get(name);
        return data != null && System.currentTimeMillis() < data[1];
    }

    /**
     * Called by {@link com.zahrproject.votingmod.network.EventTimerPacket} on the client thread.
     *
     * @param remainingMs     ms until the effect ends; ≤ 0 removes the bar
     * @param totalDurationMs full event duration used for the fill-bar fraction
     */
    public static void setTimer(String name, long remainingMs, long totalDurationMs) {
        if (remainingMs <= 0) {
            activeTimers.remove(name);
        } else {
            // Use totalDurationMs for the fraction so the bar always reflects
            // overall progress, even on reconnect mid-event.
            long duration = totalDurationMs > 0 ? totalDurationMs : remainingMs;
            activeTimers.put(name, new long[]{duration, System.currentTimeMillis() + remainingMs});
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Clear stale timers when the player disconnects from a world/server. */
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        activeTimers.clear();
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
            String name       = entry.getKey();
            long[] data       = entry.getValue();   // [totalDurationMs, expiryMs]
            long   durationMs = data[0];
            long   expiryMs   = data[1];
            long   remaining  = Math.max(0, expiryMs - now);
            float  fraction   = durationMs > 0 ? (float) remaining / durationMs : 0f;

            int x = startX;
            int y = startY + idx * (BG_H + GAP);
            int eventColor = colorForEvent(name);

            // ── Background ───────────────────────────────────────────────────
            g.fill(x, y, x + BG_W, y + BG_H, 0xCC000000);

            // ── Coloured border (1 px) ───────────────────────────────────────
            int border = withAlpha(eventColor, 0xCC);
            g.fill(x,            y,           x + BG_W,    y + 1,      border); // top
            g.fill(x,            y + BG_H - 1, x + BG_W,  y + BG_H,   border); // bottom
            g.fill(x,            y,           x + 1,       y + BG_H,   border); // left
            g.fill(x + BG_W - 1, y,           x + BG_W,   y + BG_H,   border); // right

            // ── Text row: name (left) | time (right) ────────────────────────
            long   secs    = remaining / 1000;
            String timeStr = String.format("%d:%02d", secs / 60, secs % 60);
            int    timeW   = font.width(timeStr);

            g.drawString(font, name,    x + PAD,                y + PAD, 0xFFEEEEEE, false);
            g.drawString(font, timeStr, x + BG_W - timeW - PAD, y + PAD, eventColor, false);

            // ── Depleting bar ────────────────────────────────────────────────
            int barX      = x + PAD;
            int barY      = y + PAD + 9; // 9 = font height (8) + 1 px gap
            int barInnerW = BG_W - PAD * 2;
            int filled    = Math.round(fraction * barInnerW);

            g.fill(barX, barY, barX + barInnerW, barY + BAR_H, 0xFF333333);
            if (filled > 0) {
                if (isMobEffectsEvent(name)) {
                    // Gradient bar: blue → red → green (column by column)
                    for (int px = 0; px < filled; px++) {
                        g.fill(barX + px, barY, barX + px + 1, barY + BAR_H,
                                blueRedGreenGradient((float) px / barInnerW));
                    }
                } else {
                    g.fill(barX, barY, barX + filled, barY + BAR_H, eventColor);
                }
            }

            idx++;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns a colour for the named event, falling back to green. */
    private static int colorForEvent(String name) {
        if (name.contains("дном"))       return 0xFFFF6060; // Все вверх дном       → red
        if (name.contains("Мидас"))      return 0xFFFFD700; // Мидас                → gold
        if (name.contains("детство"))    return 0xFF5BC0FF; // Обратно в детство    → light blue
        if (name.contains("агресс"))     return 0xFFBB7733; // Пассивная агрессия   → brown
        if (name.contains("сложн"))      return 0xFFDD1122; // Выше, сильнее, слож. → crimson
        if (name.contains("Голова"))     return 0xFFCC55FF; // Голова вниз          → purple
        if (name.contains("хардкор"))    return 0xFF800020; // Истинный хардкор     → dark burgundy
        if (name.contains("аквамен"))    return 0xFF4488FF; // Аквамен              → blue
        if (name.contains("хитрость"))  return 0xFF20B2AA; // Рыбацкая хитрость   → teal
        if (isMobEffectsEvent(name))  return animatedBRGColor();      // animated blue-red-green
        return 0xFF88FF88;                                 // fallback              → green
    }

    private static boolean isMobEffectsEvent(String name) {
        return name.contains("Интеграция с effects");
    }

    /**
     * Animates through blue → red → green using per-channel sine waves
     * with 120° (2π/3) phase offsets, giving a smooth RGB cycle.
     */
    private static int animatedBRGColor() {
        double t = System.currentTimeMillis() * 0.002;
        int r = (int) (127 + 127 * Math.sin(t));
        int g = (int) (127 + 127 * Math.sin(t + 2.094)); // +120°
        int b = (int) (127 + 127 * Math.sin(t + 4.189)); // +240°
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /**
     * Static blue→red→green gradient for the fill bar.
     * t ∈ [0,1]: 0..0.5 interpolates blue→red; 0.5..1 interpolates red→green.
     */
    private static int blueRedGreenGradient(float t) {
        int r, g, b;
        if (t < 0.5f) {
            float s = t * 2f;          // 0..1
            r = clamp((int) (255 * s));
            g = 0;
            b = clamp((int) (255 * (1f - s)));
        } else {
            float s = (t - 0.5f) * 2f; // 0..1
            r = clamp((int) (255 * (1f - s)));
            g = clamp((int) (255 * s));
            b = 0;
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Replaces the alpha byte of a fully-opaque ARGB colour. */
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
