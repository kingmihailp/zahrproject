package com.zahrproject.votingmod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * A Button that renders word-wrapped text inside itself,
 * so long event descriptions are always fully visible.
 *
 * Supports a custom accent color for YES (green) / NO (red) styling.
 * Default accent is blue.
 */
@OnlyIn(Dist.CLIENT)
public class MultilineButton extends Button {

    /** Preset accent colors. */
    public static final int ACCENT_BLUE  = 0x2288DD;
    public static final int ACCENT_GREEN = 0x22BB44;
    public static final int ACCENT_RED   = 0xCC2233;

    private final List<String> wrappedLines;
    private final Font font;

    // Derived colors (pre-computed in constructor)
    private final int bgNormal;
    private final int bgHover;
    private final int borderNormal;
    private final int borderHover;

    /** Blue accent (default). */
    public MultilineButton(int x, int y, int width, int height,
                           String text, Font font, OnPress onPress) {
        this(x, y, width, height, text, font, ACCENT_BLUE, onPress);
    }

    /** Custom accent color (pass one of the ACCENT_* constants or your own RGB). */
    public MultilineButton(int x, int y, int width, int height,
                           String text, Font font, int accentRgb, OnPress onPress) {
        super(x, y, width, height, Component.literal(text), onPress, supplier -> supplier.get());
        this.font = font;
        this.wrappedLines = wordWrap(text, width - 14);

        this.borderNormal = 0xFF000000 | accentRgb;
        this.borderHover  = 0xFF000000 | lighten(accentRgb, 1.55f);
        this.bgNormal     = buildBg(accentRgb, 0xCC, 0.18f);
        this.bgHover      = buildBg(accentRgb, 0xDD, 0.28f);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        int bg     = !active ? 0xAA2A2A2A : (isHovered ? bgHover     : bgNormal);
        int border = !active ? 0xFF555555  : (isHovered ? borderHover : borderNormal);

        // Background
        graphics.fill(x, y, x + w, y + h, bg);
        // Border (2px)
        graphics.fill(x,         y,         x + w,     y + 2,     border);
        graphics.fill(x,         y + h - 2, x + w,     y + h,     border);
        graphics.fill(x,         y,         x + 2,     y + h,     border);
        graphics.fill(x + w - 2, y,         x + w,     y + h,     border);

        // Wrapped text, centered vertically
        int lineH       = font.lineHeight + 3;
        int totalTextH  = wrappedLines.size() * lineH - 3;
        int textY       = y + (h - totalTextH) / 2;
        int textColor   = active ? 0xFFFFFFFF : 0xFF777777;

        for (String line : wrappedLines) {
            graphics.drawCenteredString(font, line, x + w / 2, textY, textColor);
            textY += lineH;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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

    /** Returns an ARGB color using alpha + darkened RGB. */
    private static int buildBg(int rgb, int alpha, float brightness) {
        int r = (int)(((rgb >> 16) & 0xFF) * brightness);
        int g = (int)(((rgb >> 8)  & 0xFF) * brightness);
        int b = (int)( (rgb        & 0xFF) * brightness);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /** Brightens RGB channels by the given factor (clamps to 255). */
    private static int lighten(int rgb, float factor) {
        int r = Math.min(255, (int)(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int)(((rgb >> 8)  & 0xFF) * factor));
        int b = Math.min(255, (int)( (rgb        & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }
}
