package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side renderer for the "Это точно торттил?" event.
 *
 * When a block is right-clicked while the event is active the server sends a
 * {@link com.zahrproject.votingmod.network.PrimedBlockPacket}.  This renderer
 * draws a blinking white outline-box on every such primed block.
 *
 * Blinking frequency increases and alpha grows as the fuse time runs out,
 * matching the feel of vanilla primed TNT.
 */
@OnlyIn(Dist.CLIENT)
public class PrimedBlockRenderer {

    /**
     * Tracks primed blocks: BlockPos → [startMs, fuseMs]
     * Updated from the client network thread, read on the render thread —
     * ConcurrentHashMap makes this safe.
     */
    private static final Map<BlockPos, long[]> PRIMED = new ConcurrentHashMap<>();

    /** Called by {@link ClientPacketHandlers#handlePrimedBlock}. */
    public static void addPrimed(BlockPos pos, long fuseMs) {
        PRIMED.put(pos, new long[]{ System.currentTimeMillis(), fuseMs });
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (PRIMED.isEmpty()) return;

        long now = System.currentTimeMillis();

        // Collect visible entries; prune expired ones
        List<Map.Entry<BlockPos, long[]>> visible = new ArrayList<>();
        for (Map.Entry<BlockPos, long[]> entry : PRIMED.entrySet()) {
            long startMs = entry.getValue()[0];
            long fuseMs  = entry.getValue()[1];
            long elapsed = now - startMs;

            if (elapsed >= fuseMs) {
                PRIMED.remove(entry.getKey());
                continue;
            }

            // Blink: period shrinks from 250 ms → 60 ms as fuse expires
            long period = Math.max(60L, 250L - (elapsed * 190L) / fuseMs);
            if ((now / period) % 2 == 0) {
                visible.add(entry);
            }
        }

        if (visible.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3   camPos = camera.getPosition();
        Matrix4f m    = event.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();

        Tesselator    tess = Tesselator.getInstance();
        BufferBuilder buf  = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Map.Entry<BlockPos, long[]> entry : visible) {
            BlockPos pos     = entry.getKey();
            long     startMs = entry.getValue()[0];
            long     fuseMs  = entry.getValue()[1];
            long     elapsed = now - startMs;

            // Alpha grows from 0.25 → 0.80 as fuse expires
            float alpha = 0.25f + 0.55f * (float) elapsed / fuseMs;

            float e  = 0.003f; // slight expand to avoid z-fighting
            float x0 = (float)(pos.getX() - camPos.x) - e;
            float y0 = (float)(pos.getY() - camPos.y) - e;
            float z0 = (float)(pos.getZ() - camPos.z) - e;
            float x1 = x0 + 1f + 2 * e;
            float y1 = y0 + 1f + 2 * e;
            float z1 = z0 + 1f + 2 * e;

            drawBox(buf, m, x0, y0, z0, x1, y1, z1, alpha);
        }

        tess.end();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void drawBox(BufferBuilder buf, Matrix4f m,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float alpha) {
        // Top (y+)
        buf.vertex(m, x0, y1, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x0, y1, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y1, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y1, z0).color(1f, 1f, 1f, alpha).endVertex();
        // Bottom (y-)
        buf.vertex(m, x0, y0, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y0, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y0, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x0, y0, z1).color(1f, 1f, 1f, alpha).endVertex();
        // North (z-)
        buf.vertex(m, x0, y0, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x0, y1, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y1, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y0, z0).color(1f, 1f, 1f, alpha).endVertex();
        // South (z+)
        buf.vertex(m, x0, y0, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y0, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y1, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x0, y1, z1).color(1f, 1f, 1f, alpha).endVertex();
        // West (x-)
        buf.vertex(m, x0, y0, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x0, y0, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x0, y1, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x0, y1, z0).color(1f, 1f, 1f, alpha).endVertex();
        // East (x+)
        buf.vertex(m, x1, y0, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y1, z0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y1, z1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, x1, y0, z1).color(1f, 1f, 1f, alpha).endVertex();
    }
}
