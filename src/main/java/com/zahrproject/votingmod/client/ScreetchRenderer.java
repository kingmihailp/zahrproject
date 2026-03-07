package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zahrproject.votingmod.entity.ScreetchEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Рендерер скритча.
 *
 * Использует {@link ScreetchModel} и текстуру textures/entity/screetch.png.
 * Глаза рендерятся отдельным проходом с EYES рендертипом (полное свечение,
 * игнорирует освещение уровня) — это создаёт эффект светящихся глаз.
 */
@OnlyIn(Dist.CLIENT)
public class ScreetchRenderer extends MobRenderer<ScreetchEntity, ScreetchModel> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    new ResourceLocation("votingmod", "screetch"), "main");

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("votingmod", "textures/entity/screetch.png");
    private static final ResourceLocation TEXTURE_EYES =
            new ResourceLocation("votingmod", "textures/entity/screetch_eyes.png");

    public ScreetchRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ScreetchModel(ctx.bakeLayer(LAYER_LOCATION)), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(ScreetchEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ScreetchEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        // Second pass: render eyes with full-bright (EYES render type, like spiders/endermen)
        this.model.renderToBuffer(
                poseStack,
                bufferSource.getBuffer(RenderType.eyes(TEXTURE_EYES)),
                15728880, // max brightness
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, 1f);
    }
}
