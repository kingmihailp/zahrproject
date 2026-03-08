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
 * Модель скритча.
 *
 * Соглашение координат: рендерер применяет scale(1, -1, 1) (Y-flip),
 * поэтому здесь используется стандартное MC соглашение Y+ = вниз.
 *
 * Тело body 10×10×10 с pivot Y=18 → центр на высоте 0.3 блока после translate.
 * Глаза (маленькие, 2×1): Y=-3 от body = чуть ВЫШЕ центра тела.
 * Улыбка (3 части): Y=0..1 по центру + Y=-1..0 по углам (∪-форма = улыбка).
 * Лапки: 3 пары, каждая из 2 сегментов (верхний — child body, нижний — child root).
 *
 * Текстурная развёртка (64×64):
 *   body        : texOffs(0,0)   → 40×20
 *   eyeL        : texOffs(0,32)  → 6×2
 *   eyeR        : texOffs(8,32)  → 6×2
 *   jawCenter   : texOffs(0,44)  → 10×2
 *   jawLeft     : texOffs(11,44) → 6×2
 *   jawRight    : texOffs(18,44) → 6×2
 *   legs        : texOffs(28,32) → оставшаяся область
 */
@OnlyIn(Dist.CLIENT)
public class ScreetchModel extends EntityModel<ScreetchEntity> {

    private final ModelPart body;
    private final ModelPart eyeL;
    private final ModelPart eyeR;
    private final ModelPart jawCenter;
    private final ModelPart jawLeft;
    private final ModelPart jawRight;

    private final ModelPart[] legUpperL = new ModelPart[3];
    private final ModelPart[] legLowerL = new ModelPart[3];
    private final ModelPart[] legUpperR = new ModelPart[3];
    private final ModelPart[] legLowerR = new ModelPart[3];

    // ── LayerDefinition ───────────────────────────────────────────────────────

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ── Body ──────────────────────────────────────────────────────────────
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-5, -5, -5, 10, 10, 10),
                PartPose.offset(0, 18, 0));

        // ── Глаза — маленькие (2×1), чуть выше центра тела (Y=-3) ─────────────
        body.addOrReplaceChild("eyeL",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(-3f, -3f, -5.5f, 2, 1, 1),
                PartPose.ZERO);

        body.addOrReplaceChild("eyeR",
                CubeListBuilder.create()
                        .texOffs(8, 32)
                        .addBox(1f, -3f, -5.5f, 2, 1, 1),
                PartPose.ZERO);

        // ── Улыбка: три части в форме ∪ ──────────────────────────────────────
        // Центр чуть ниже (Y=0..1), углы чуть выше (Y=-1..0) → улыбка.
        body.addOrReplaceChild("jawCenter",
                CubeListBuilder.create()
                        .texOffs(0, 44)
                        .addBox(-2f, 0f, -5.5f, 4, 1, 1),
                PartPose.ZERO);

        body.addOrReplaceChild("jawLeft",
                CubeListBuilder.create()
                        .texOffs(11, 44)
                        .addBox(-4f, -1f, -5.5f, 2, 1, 1),
                PartPose.ZERO);

        body.addOrReplaceChild("jawRight",
                CubeListBuilder.create()
                        .texOffs(18, 44)
                        .addBox(2f, -1f, -5.5f, 2, 1, 1),
                PartPose.ZERO);

        // ── Лапки ─────────────────────────────────────────────────────────────
        // С Y-flip: верхний сегмент от body уходит вниз при rotation=-45° (левые)
        // или +45° (правые). Нижний сегмент стыкуется с кончиком верхнего.
        //
        // Верхний кончик с rotation=-45° (левые): body_local(-5,0) →
        //   tip_body_local = (-5-3.54, 0+3.54) = (-8.54, 3.54)
        //   root = (-5+(-8.54), 18+3.54) = (нет, pivot в body space)
        //   root = body_pivot + tip = (0-5-3.54, 18+3.54) = (-8.54, 21.54)
        //
        // Нижний pivot стыкуется при root Y=21.5.
        float[] legZ = {-3f, 0f, 3f};

        for (int i = 0; i < 3; i++) {
            // Левые верхние — rotation -45° (с Y-flip это DOWN-LEFT в мире)
            body.addOrReplaceChild("legUpperL" + i,
                    CubeListBuilder.create()
                            .texOffs(28, 32 + i * 5)
                            .addBox(-5f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(-5, 0, legZ[i],
                            0, 0, (float) Math.toRadians(-45)));

            // Нижние левые: pivot на root Y=21.5 (стыкуется с кончиком верхнего)
            root.addOrReplaceChild("legLowerL" + i,
                    CubeListBuilder.create()
                            .texOffs(40, 32 + i * 5)
                            .addBox(-5f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(-8.5f, 21.5f, legZ[i],
                            0, 0, (float) Math.toRadians(45)));

            // Правые верхние — rotation +45° (с Y-flip это DOWN-RIGHT в мире)
            body.addOrReplaceChild("legUpperR" + i,
                    CubeListBuilder.create()
                            .texOffs(28, 47 + i * 5)
                            .addBox(0f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(5, 0, legZ[i],
                            0, 0, (float) Math.toRadians(45)));

            // Нижние правые
            root.addOrReplaceChild("legLowerR" + i,
                    CubeListBuilder.create()
                            .texOffs(40, 47 + i * 5)
                            .addBox(0f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(8.5f, 21.5f, legZ[i],
                            0, 0, (float) Math.toRadians(-45)));
        }

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

        for (int i = 0; i < 3; i++) {
            legUpperL[i] = body.getChild("legUpperL" + i);
            legUpperR[i] = body.getChild("legUpperR" + i);
            legLowerL[i] = root.getChild("legLowerL" + i);
            legLowerR[i] = root.getChild("legLowerR" + i);
        }
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    @Override
    public void setupAnim(ScreetchEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Лёгкое покачивание тела
        body.y = 18f + (float) Math.sin(ageInTicks * 0.1f) * 0.5f;

        // Открывание пасти по мере приближения укуса
        float warnProgress = entity.getBiteTimer() / (float) ScreetchEntity.BITE_TICKS;
        float jawOpen = warnProgress * 0.5f;
        jawCenter.xRot = jawOpen;
        jawLeft.xRot   = jawOpen;
        jawRight.xRot  = jawOpen;

        // Дрожание тела ближе к укусу
        if (warnProgress > 0.7f) {
            float tremble = (float) Math.sin(ageInTicks * 0.8f) * 0.05f
                    * (warnProgress - 0.7f) / 0.3f;
            body.xRot = tremble;
            body.zRot = tremble * 0.7f;
        } else {
            body.xRot = 0;
            body.zRot = 0;
        }

        // Волновое движение лапок
        for (int i = 0; i < 3; i++) {
            float phase = ageInTicks * 0.15f + i * 1.2f;
            float wave  = (float) Math.sin(phase) * 0.3f;

            legUpperL[i].zRot = (float) Math.toRadians(-45) + wave;
            legUpperR[i].zRot = (float) Math.toRadians(45)  - wave;
            legLowerL[i].zRot = (float) Math.toRadians(45)  - wave * 0.5f;
            legLowerR[i].zRot = (float) Math.toRadians(-45) + wave * 0.5f;
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        // body.render рендерит само тело И всех его children (глаза, пасть, верхние лапки)
        body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        // Нижние лапки прикреплены к root, рендерим отдельно
        for (int i = 0; i < 3; i++) {
            legLowerL[i].render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            legLowerR[i].render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}
