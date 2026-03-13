package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom renderer for the «Дюп» item.
 *
 * <p>Every render frame a different sprite is sampled from the block/item
 * texture atlas, cycling through all game textures at ~10 frames per second.
 * The chosen sprite is also tinted with a continuously-cycling rainbow hue,
 * producing the "all shimmering textures of the game" look.
 *
 * <p>The sprite list is lazily cached and refreshed every 10 seconds so
 * resource-pack reloads are picked up automatically.
 */
@OnlyIn(Dist.CLIENT)
public class DupItemRenderer extends BlockEntityWithoutLevelRenderer {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static DupItemRenderer INSTANCE;

    public static DupItemRenderer getInstance() {
        if (INSTANCE == null) {
            Minecraft mc = Minecraft.getInstance();
            INSTANCE = new DupItemRenderer(mc);
        }
        return INSTANCE;
    }

    private DupItemRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    // ── Sprite cache ──────────────────────────────────────────────────────────

    private static List<TextureAtlasSprite> cachedSprites = null;
    private static long cacheTimestamp = 0L;
    private static final long CACHE_TTL_MS = 10_000L;

    private static List<TextureAtlasSprite> getSprites() {
        long now = System.currentTimeMillis();
        if (cachedSprites != null && now - cacheTimestamp < CACHE_TTL_MS) {
            return cachedSprites;
        }
        cachedSprites = buildSpriteList();
        cacheTimestamp = now;
        return cachedSprites;
    }

    @SuppressWarnings("unchecked")
    private static List<TextureAtlasSprite> buildSpriteList() {
        try {
            TextureAtlas atlas = Minecraft.getInstance()
                    .getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);

            for (Field f : TextureAtlas.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object val = f.get(atlas);
                if (!(val instanceof Map<?, ?> m) || m.isEmpty()) continue;
                Map.Entry<?, ?> sample = m.entrySet().iterator().next();
                if (sample.getKey() instanceof ResourceLocation
                        && sample.getValue() instanceof TextureAtlasSprite) {
                    return new ArrayList<>(
                            ((Map<ResourceLocation, TextureAtlasSprite>) m).values());
                }
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Texture cycles every 100 ms (~10 distinct sprites per second).
     * Hue cycles independently every 2 000 ms for the rainbow colour tint.
     */
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx,
                             PoseStack poseStack, MultiBufferSource bufferSource,
                             int packedLight, int packedOverlay) {

        List<TextureAtlasSprite> sprites = getSprites();
        if (sprites.isEmpty()) return;

        long now = System.currentTimeMillis();

        // Pick which sprite to show based on time
        int spriteIdx = (int) ((now / 100L) % sprites.size());
        TextureAtlasSprite sprite = sprites.get(spriteIdx);

        // Compute rainbow colour tint
        float hue   = (now % 2_000L) / 2_000.0f;
        int   rgb   = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
        int   tintR = (rgb >> 16) & 0xFF;
        int   tintG = (rgb >>  8) & 0xFF;
        int   tintB =  rgb        & 0xFF;

        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        VertexConsumer vc = bufferSource.getBuffer(
                RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));

        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat  = pose.pose();
        Matrix3f norm = pose.normal();

        // Front face (+Z)
        emitQuad(vc, mat, norm, packedLight, packedOverlay, tintR, tintG, tintB,
                 0f, 0f, 0.0625f,
                 1f, 0f, 0.0625f,
                 1f, 1f, 0.0625f,
                 0f, 1f, 0.0625f,
                 u0, v1, u1, v1, u1, v0, u0, v0,
                 0f, 0f, 1f);

        // Back face (-Z, U flipped so texture isn't mirrored)
        emitQuad(vc, mat, norm, packedLight, packedOverlay, tintR, tintG, tintB,
                 1f, 0f, 0f,
                 0f, 0f, 0f,
                 0f, 1f, 0f,
                 1f, 1f, 0f,
                 u0, v1, u1, v1, u1, v0, u0, v0,
                 0f, 0f, -1f);
    }

    private static void emitQuad(VertexConsumer vc, Matrix4f mat, Matrix3f norm,
                                  int light, int overlay,
                                  int r, int g, int b,
                                  float x0, float y0, float z0,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float x3, float y3, float z3,
                                  float u0, float v0, float u1, float v1,
                                  float u2, float v2, float u3, float v3,
                                  float nx, float ny, float nz) {
        vc.vertex(mat, x0, y0, z0).color(r, g, b, 255).uv(u0, v0).overlayCoords(overlay).uv2(light).normal(norm, nx, ny, nz).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, 255).uv(u1, v1).overlayCoords(overlay).uv2(light).normal(norm, nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, 255).uv(u2, v2).overlayCoords(overlay).uv2(light).normal(norm, nx, ny, nz).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, 255).uv(u3, v3).overlayCoords(overlay).uv2(light).normal(norm, nx, ny, nz).endVertex();
    }
}
