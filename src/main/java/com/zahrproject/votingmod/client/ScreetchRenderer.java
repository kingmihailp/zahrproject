package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zahrproject.votingmod.entity.ScreetchEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Рендерер скритча.
 *
 * Extends EntityRenderer (не MobRenderer) потому что ScreetchEntity
 * наследуется от Entity, а не от Mob.
 *
 * Два прохода:
 *   1. Основная текстура (screetch.png) — обычное освещение.
 *   2. Текстура глаз   (screetch_eyes.png) — RenderType.eyes (полное свечение).
 */
@OnlyIn(Dist.CLIENT)
public class ScreetchRenderer extends EntityRenderer<ScreetchEntity> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    new ResourceLocation("votingmod", "screetch"), "main");

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("votingmod", "textures/entity/screetch.png");
    private static final ResourceLocation TEXTURE_EYES =
            new ResourceLocation("votingmod", "textures/entity/screetch_eyes.png");

    private final ScreetchModel model;

    public ScreetchRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new ScreetchModel(ctx.bakeLayer(LAYER_LOCATION));
    }

    @Override
    public ResourceLocation getTextureLocation(ScreetchEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ScreetchEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // Rotate so the screetch faces its yaw
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - entityYaw));

        // Minecraft ModelPart уже делит все координаты на 16 при запекании (1 пиксель = 1/16 блока).
        // Внешний scale(1/16) НЕ нужен — он только делал бы двойное деление, уменьшая модель
        // до 10/256 ≈ 0.04 блока (полная невидимость).
        //
        // Смещаем модель вниз, чтобы тело (pivot Y=18 пикселей = 18/16 = 1.125 блока)
        // оказалось в центре хитбокса (bbHeight/2 = 0.3 блока).
        // ty = bbHeight/2 − 18/16 = 0.3 − 1.125 = −0.825
        poseStack.translate(0.0f, entity.getBbHeight() / 2.0f - 18.0f / 16.0f, 0.0f);

        // Animate the model
        model.setupAnim(entity,
                0f, 0f,
                entity.tickCount + partialTick,
                entity.getYRot() - entity.yRotO,
                entity.getXRot());

        // Pass 1: normal texture
        model.renderToBuffer(poseStack,
                bufferSource.getBuffer(RenderType.entityCutout(TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, 1f);

        // Pass 2: glowing eyes (ignores light level — always fully bright)
        model.renderToBuffer(poseStack,
                bufferSource.getBuffer(RenderType.eyes(TEXTURE_EYES)),
                15728880, OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, 1f);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
