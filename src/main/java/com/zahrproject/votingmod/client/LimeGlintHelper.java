package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * Provides lime-coloured enchantment glint render types for the Terra Blade.
 *
 * Extends RenderStateShard solely to access the protected singletons
 * (RENDERTYPE_GLINT_DIRECT_SHADER, GLINT_TRANSPARENCY, etc.) without
 * reflection or an access transformer.
 */
public final class LimeGlintHelper extends RenderStateShard {

    // Required by RenderStateShard — never instantiated
    private LimeGlintHelper() {
        super("lime_glint_helper", () -> {}, () -> {});
    }

    public static final ResourceLocation LIME_GLINT_TEXTURE =
            new ResourceLocation("votingmod", "textures/misc/lime_glint.png");

    /**
     * Replacement for {@link RenderType#glintDirect()} — used in GUI / flat lighting.
     */
    public static final RenderType LIME_GLINT_DIRECT = RenderType.create(
            "votingmod:lime_glint_direct",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_GLINT_DIRECT_SHADER)
                    .setTextureState(new TextureStateShard(LIME_GLINT_TEXTURE, true, false))
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(EQUAL_DEPTH_TEST)
                    .setTransparencyState(GLINT_TRANSPARENCY)
                    .createCompositeState(false));

    /**
     * Replacement for {@link RenderType#glintTranslucent()} — used in 3-D world rendering.
     */
    public static final RenderType LIME_GLINT_TRANSLUCENT = RenderType.create(
            "votingmod:lime_glint_translucent",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_GLINT_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(LIME_GLINT_TEXTURE, true, false))
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(EQUAL_DEPTH_TEST)
                    .setTransparencyState(GLINT_TRANSPARENCY)
                    .createCompositeState(false));

    /**
     * Generates a 64×32 lime-green swirl texture and registers it with the
     * texture manager.  Must be called on the render thread (FMLClientSetupEvent).
     */
    public static DynamicTexture createTexture() {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 64, 32, false);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                double u = x / 64.0;
                double v = y / 32.0;
                // Overlapping sine waves to produce swirl-like variation
                double wave = Math.sin(u * Math.PI * 6.0 + v * Math.PI * 4.0)
                            + 0.5 * Math.sin(u * Math.PI * 10.0 - v * Math.PI * 6.0);
                float t = Math.max(0f, Math.min(1f, (float)(wave * 0.25 + 0.5)));

                int r = (int)(30  * t);
                int g = (int)(220 * t);
                int b = (int)(50  * t);
                int a = (int)(140 + 115 * t);

                // NativeImage.setPixelRGBA expects ABGR-packed int
                img.setPixelRGBA(x, y, FastColor.ABGR32.color(a, b, g, r));
            }
        }
        return new DynamicTexture(img);
    }
}
