package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Render layer added to <em>every</em> {@code LivingEntityRenderer} via
 * {@link com.zahrproject.votingmod.mixin.LivingEntityRendererMixin}.
 *
 * When the entity's custom name is {@code "_jeb"}, the entity's own model is
 * rendered a second time on top with a continuously cycling rainbow tint —
 * exactly the same technique used by vanilla Minecraft for {@code _jeb} sheep,
 * generalised to work for any living entity.
 *
 * Cycle timing: the hue completes one full revolution every 25 ticks (1.25 s),
 * smoothly interpolated using {@code partialTick} so animation runs at full
 * frame-rate even on slow servers.
 */
@OnlyIn(Dist.CLIENT)
public class JebRainbowLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {

    public JebRainbowLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, T entity,
                       float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {

        if (!entity.hasCustomName()) return;
        if (!"_jeb".equals(entity.getCustomName().getString())) return;

        // ── Compute rainbow colour ─────────────────────────────────────────────
        // Use the same 25-tick cycle as vanilla's _jeb sheep, with partial-tick
        // interpolation for smooth per-frame animation.
        float progress = (entity.tickCount + partialTick) / 25.0f;
        float hue     = progress % 1.0f;

        int   rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        float r   = ((rgb >> 16) & 0xFF) / 255.0f;
        float g   = ((rgb >>  8) & 0xFF) / 255.0f;
        float b   = ( rgb        & 0xFF) / 255.0f;

        // ── Re-render the entity model with the rainbow tint ──────────────────
        // Using the entity's own texture preserves all surface detail while
        // the colour multiplier shifts the whole model through the rainbow.
        ResourceLocation texture = this.renderer.getTextureLocation(entity);

        getParentModel().renderToBuffer(
                poseStack,
                bufferSource.getBuffer(RenderType.entityTranslucent(texture)),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                r, g, b, 0.9f   // strong tint, barely translucent (like wool on _jeb sheep)
        );
    }
}
