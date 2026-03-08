package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zahrproject.votingmod.entity.ScreetchEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Модель скритча — стандартное MC соглашение Y+ = вниз (к ногам).
 * Рендерер применяет scale(1,-1,1) для корректной ориентации.
 *
 * Тело body 10×10×10, pivot root Y=18 → центр тела на 0.3 блока выше земли.
 *
 * Глаза 2×2×1 (маленькие), цвет RED в основной текстуре и в eyes-текстуре.
 *
 * 4 лапки как лучи солнца (каждая — дочерний элемент body):
 *   legLeft  : от левой  грани (-X), уходит влево
 *   legRight : от правой грани (+X), уходит вправо
 *   legTop   : от верхней грани (model −Y = мир +Y), уходит вверх
 *   legBottom: от нижней грани (model +Y = мир −Y), уходит вниз
 *
 * UV (64×64):
 *   body      : texOffs(0,0)   40×20
 *   eyeL      : texOffs(0,32)   6×3  front→ u=1..2, v=33..34
 *   eyeR      : texOffs(8,32)   6×3  front→ u=9..10, v=33..34
 *   jawCenter : texOffs(0,44)  10×3  front→ u=1..4, v=45..46 (зубы)
 *   jawLeft   : texOffs(11,44)  6×3
 *   jawRight  : texOffs(18,44)  6×3
 *   legLeft   : texOffs(28,32) 20×4
 *   legRight  : texOffs(28,36) 20×4
 *   legTop    : texOffs(28,40)  8×10
 *   legBottom : texOffs(36,40)  8×10
 */
@OnlyIn(Dist.CLIENT)
public class ScreetchModel extends EntityModel<ScreetchEntity> {

    private final ModelPart body;
    private final ModelPart eyeL;
    private final ModelPart eyeR;
    private final ModelPart jawCenter;
    private final ModelPart jawLeft;
    private final ModelPart jawRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;
    private final ModelPart legTop;
    private final ModelPart legBottom;

    // ── LayerDefinition ───────────────────────────────────────────────────────

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ── Body 10×10×10 ─────────────────────────────────────────────────────
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5, -5, -5, 10, 10, 10),
                PartPose.offset(0, 18, 0));

        // ── Глаза 2×2×1, красные, без внешнего масштаба ──────────────────────
        body.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(0, 32).addBox(-3f, -4f, -5.5f, 2, 2, 1),
                PartPose.ZERO);
        body.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(8, 32).addBox(1f, -4f, -5.5f, 2, 2, 1),
                PartPose.ZERO);

        // ── Улыбка ∪ с зубами ────────────────────────────────────────────────
        body.addOrReplaceChild("jawCenter",
                CubeListBuilder.create().texOffs(0, 44).addBox(-2f, 0f, -5.5f, 4, 2, 1),
                PartPose.ZERO);
        body.addOrReplaceChild("jawLeft",
                CubeListBuilder.create().texOffs(11, 44).addBox(-4f, -2f, -5.5f, 2, 2, 1),
                PartPose.ZERO);
        body.addOrReplaceChild("jawRight",
                CubeListBuilder.create().texOffs(18, 44).addBox(2f, -2f, -5.5f, 2, 2, 1),
                PartPose.ZERO);

        // ── Лапки: 4 луча от граней тела (кроме переда и зада) ───────────────
        // legLeft: от левой грани (body-local X=-5), уходит ещё левее (-X)
        body.addOrReplaceChild("legLeft",
                CubeListBuilder.create().texOffs(28, 32).addBox(-8f, -1f, -1f, 8, 2, 2),
                PartPose.offset(-5, 0, 0));

        // legRight: от правой грани (body-local X=+5), уходит вправо (+X)
        body.addOrReplaceChild("legRight",
                CubeListBuilder.create().texOffs(28, 36).addBox(0f, -1f, -1f, 8, 2, 2),
                PartPose.offset(5, 0, 0));

        // legTop: от верхней грани (body-local Y=-5).
        // С Y-flip: model -Y = мир +Y → уходит ВВЕРХ ✓
        body.addOrReplaceChild("legTop",
                CubeListBuilder.create().texOffs(28, 40).addBox(-1f, -8f, -1f, 2, 8, 2),
                PartPose.offset(0, -5, 0));

        // legBottom: от нижней грани (body-local Y=+5).
        // С Y-flip: model +Y = мир -Y → уходит ВНИЗ ✓
        body.addOrReplaceChild("legBottom",
                CubeListBuilder.create().texOffs(36, 40).addBox(-1f, 0f, -1f, 2, 8, 2),
                PartPose.offset(0, 5, 0));

        return LayerDefinition.create(mesh, 64, 64);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public ScreetchModel(ModelPart root) {
        this.body      = root.getChild("body");
        this.eyeL      = body.getChild("eyeL");
        this.eyeR      = body.getChild("eyeR");
        this.jawCenter = body.getChild("jawCenter");
        this.jawLeft   = body.getChild("jawLeft");
        this.jawRight  = body.getChild("jawRight");
        this.legLeft   = body.getChild("legLeft");
        this.legRight  = body.getChild("legRight");
        this.legTop    = body.getChild("legTop");
        this.legBottom = body.getChild("legBottom");
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    @Override
    public void setupAnim(ScreetchEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Лёгкое покачивание тела вверх-вниз
        body.y = 18f + (float) Math.sin(ageInTicks * 0.1f) * 0.5f;

        // Открывание рта при атаке
        float warnProgress = entity.getBiteTimer() / (float) ScreetchEntity.BITE_TICKS;
        float jawOpen = warnProgress * 0.5f;
        jawCenter.xRot = jawOpen;
        jawLeft.xRot   = jawOpen;
        jawRight.xRot  = jawOpen;

        // Дрожание тела в последний момент перед укусом
        if (warnProgress > 0.7f) {
            float t = (float) Math.sin(ageInTicks * 0.8f) * 0.05f * (warnProgress - 0.7f) / 0.3f;
            body.xRot = t; body.zRot = t * 0.7f;
        } else {
            body.xRot = 0; body.zRot = 0;
        }

        // Плавное покачивание лапок
        float wave = (float) Math.sin(ageInTicks * 0.12f) * 0.25f;
        // Левая и правая: покачиваются вверх-вниз (zRot)
        legLeft.zRot  =  wave;
        legRight.zRot = -wave;
        // Верхняя и нижняя: покачиваются вперёд-назад (xRot)
        legTop.xRot    =  wave * 0.6f;
        legBottom.xRot = -wave * 0.6f;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        // body.render рекурсивно рендерит тело + все дочерние элементы (глаза, рот, лапки)
        body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
