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
 * Тело body 10×10×10, pivot Y=18 → центр на 0.3 блока после translate.
 *
 * Глаза 2×2 (small): body-local Y=-4 to -2 → world Y 0.425-0.55 (верх тела).
 * Улыбка 3 части: jawCenter 4×2 (с зубами) + jawLeft/Right 2×2 (углы ∪-формы).
 * Лапки: 3 пары × 2 сегмента. С Y-flip:
 *   upper: rot=-45°(left), +45°(right) → уходят ВНИЗ от тела ✓
 *   lower pivot root Y=21.5 (стыкуется с кончиком upper при тех же углах)
 *   lower: rot=-45°(left), +45°(right) → кончик root Y=25 → world Y=-0.14 (в земле) ✓
 *
 * UV (64×64):
 *   body      : texOffs(0,0)   40×20
 *   eyeL      : texOffs(0,32)  6×3
 *   eyeR      : texOffs(8,32)  6×3
 *   jawCenter : texOffs(0,44)  10×3
 *   jawLeft   : texOffs(11,44) 6×3
 *   jawRight  : texOffs(18,44) 6×3
 *   legs      : texOffs(28,32) zone
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

        // ── Body 10×10×10 ─────────────────────────────────────────────────────
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5, -5, -5, 10, 10, 10),
                PartPose.offset(0, 18, 0));

        // ── Глаза 2×2 (маленькие, чуть выше центра) ─────────────────────────
        // body-local Y=-4 to -2 → с Y-flip: world Y 0.425–0.55 (верх тела)
        body.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(0, 32).addBox(-3f, -4f, -5.5f, 2, 2, 1),
                PartPose.ZERO);
        body.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(8, 32).addBox(1f, -4f, -5.5f, 2, 2, 1),
                PartPose.ZERO);

        // ── Улыбка ∪ с зубами ────────────────────────────────────────────────
        // jawCenter 4×2: центр (ниже), зубы нарисованы на текстуре
        body.addOrReplaceChild("jawCenter",
                CubeListBuilder.create().texOffs(0, 44).addBox(-2f, 0f, -5.5f, 4, 2, 1),
                PartPose.ZERO);
        // Углы улыбки чуть выше центра (Y=-2 вместо Y=0) → ∪-форма
        body.addOrReplaceChild("jawLeft",
                CubeListBuilder.create().texOffs(11, 44).addBox(-4f, -2f, -5.5f, 2, 2, 1),
                PartPose.ZERO);
        body.addOrReplaceChild("jawRight",
                CubeListBuilder.create().texOffs(18, 44).addBox(2f, -2f, -5.5f, 2, 2, 1),
                PartPose.ZERO);

        // ── Лапки ─────────────────────────────────────────────────────────────
        // С Y-flip:
        //   upper rot=-45°(L)/+45°(R): кончик root Y=21.54 → world Y≈0.078 (к земле) ✓
        //   lower pivot root Y=21.5 стыкуется с кончиком upper ✓
        //   lower rot=-45°(L)/+45°(R): кончик root Y=25.04 → world Y=-0.14 (в землю) ✓
        float[] legZ = {-3f, 0f, 3f};
        for (int i = 0; i < 3; i++) {
            body.addOrReplaceChild("legUpperL" + i,
                    CubeListBuilder.create().texOffs(28, 32 + i * 5).addBox(-5f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(-5, 0, legZ[i], 0, 0, (float) Math.toRadians(-45)));

            root.addOrReplaceChild("legLowerL" + i,
                    CubeListBuilder.create().texOffs(40, 32 + i * 5).addBox(-5f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(-8.5f, 21.5f, legZ[i], 0, 0, (float) Math.toRadians(-45)));

            body.addOrReplaceChild("legUpperR" + i,
                    CubeListBuilder.create().texOffs(28, 47 + i * 5).addBox(0f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(5, 0, legZ[i], 0, 0, (float) Math.toRadians(45)));

            root.addOrReplaceChild("legLowerR" + i,
                    CubeListBuilder.create().texOffs(40, 47 + i * 5).addBox(0f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(8.5f, 21.5f, legZ[i], 0, 0, (float) Math.toRadians(45)));
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
        body.y = 18f + (float) Math.sin(ageInTicks * 0.1f) * 0.5f;

        float warnProgress = entity.getBiteTimer() / (float) ScreetchEntity.BITE_TICKS;
        float jawOpen = warnProgress * 0.5f;
        jawCenter.xRot = jawOpen;
        jawLeft.xRot   = jawOpen;
        jawRight.xRot  = jawOpen;

        if (warnProgress > 0.7f) {
            float t = (float) Math.sin(ageInTicks * 0.8f) * 0.05f * (warnProgress - 0.7f) / 0.3f;
            body.xRot = t; body.zRot = t * 0.7f;
        } else {
            body.xRot = 0; body.zRot = 0;
        }

        for (int i = 0; i < 3; i++) {
            float wave = (float) Math.sin(ageInTicks * 0.15f + i * 1.2f) * 0.3f;
            // С Y-flip: -45°(left)/+45°(right) → ноги уходят ВНИЗ
            legUpperL[i].zRot = (float) Math.toRadians(-45) + wave;
            legUpperR[i].zRot = (float) Math.toRadians(45)  - wave;
            // lower: тот же знак — продолжают движение вниз к земле
            legLowerL[i].zRot = (float) Math.toRadians(-45) + wave * 0.5f;
            legLowerR[i].zRot = (float) Math.toRadians(45)  - wave * 0.5f;
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        for (int i = 0; i < 3; i++) {
            legLowerL[i].render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            legLowerR[i].render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}
