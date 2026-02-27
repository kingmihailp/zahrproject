package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A Button that renders word-wrapped text inside itself,
 * so long option descriptions are fully visible.
 */
public class MultilineButton extends Button {

    private final List<String> wrappedLines;
    private final Font font;

    public MultilineButton(int x, int y, int width, int height,
                           String text, Font font, OnPress onPress) {
        super(x, y, width, height, Component.literal(text), onPress,
                supplier -> supplier.get());
        this.font = font;
        this.wrappedLines = wordWrap(text, width - 14);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // Background color based on state
        int bgColor;
        int borderColor;
        if (!active) {
            bgColor   = 0xAA2A2A2A;
            borderColor = 0xFF555555;
        } else if (isHovered) {
            bgColor   = 0xDD1A5EA0;
            borderColor = 0xFF55CCFF;
        } else {
            bgColor   = 0xCC0D3D72;
            borderColor = 0xFF2288DD;
        }

        // Fill background
        graphics.fill(x, y, x + w, y + h, bgColor);
        // Border top/bottom/left/right (2px)
        graphics.fill(x,         y,         x + w,     y + 2,     borderColor);
        graphics.fill(x,         y + h - 2, x + w,     y + h,     borderColor);
        graphics.fill(x,         y,         x + 2,     y + h,     borderColor);
        graphics.fill(x + w - 2, y,         x + w,     y + h,     borderColor);

        // Draw wrapped text lines centered vertically and horizontally
        int lineH = font.lineHeight + 3;
        int totalTextH = wrappedLines.size() * lineH - 3;
        int textStartY = y + (h - totalTextH) / 2;
        int textColor = active ? 0xFFFFFFFF : 0xFF777777;

        for (String line : wrappedLines) {
            graphics.drawCenteredString(font, line, x + w / 2, textStartY, textColor);
            textStartY += lineH;
        }
    }

    /**
     * Word-wraps text so each line fits within maxPixelWidth pixels.
     */
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
}
