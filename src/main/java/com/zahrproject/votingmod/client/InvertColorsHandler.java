package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

/**
 * Client-side handler for the "Взгляд эндермэна" voting event.
 *
 * Draws a fullscreen white quad with ONE_MINUS_DST_COLOR blending, which
 * mathematically inverts every pixel already on screen:
 *   result = (1,1,1) * (1 - dst) = 1 - dst
 *
 * Rendered after the HOTBAR overlay so it sits on top of the entire HUD.
 */
@OnlyIn(Dist.CLIENT)
public class InvertColorsHandler {

    private static boolean active = false;

    /** Called by {@link com.zahrproject.votingmod.network.InvertColorsPacket} on the client thread. */
    public static void setActive(boolean value) {
        active = value;
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!active) return;
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc     = Minecraft.getInstance();
        int       width  = mc.getWindow().getGuiScaledWidth();
        int       height = mc.getWindow().getGuiScaledHeight();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ZERO
        );
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = event.getGuiGraphics().pose().last().pose();
        Tesselator    tess = Tesselator.getInstance();
        BufferBuilder buf  = tess.getBuilder();

        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(matrix, 0,     0,      0).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(matrix, 0,     height, 0).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(matrix, width, height, 0).color(1f, 1f, 1f, 1f).endVertex();
        buf.vertex(matrix, width, 0,      0).color(1f, 1f, 1f, 1f).endVertex();
        tess.end();

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
