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
 * Модель скритча:
 *
 *   • Круглое тело (body) — чёрный шар ~10×10×10
 *   • Два больших глаза (eyeL, eyeR) — белые куби
 *   • Широкая пасть с зубами (jaw)
 *   • 6 тонких паучьих лапок (3 слева, 3 справа),
 *     каждая состоит из двух сегментов — «плечо» и «предплечье»
 *
 * Текстурная развёртка (64×64):
 *   body        : 0,0  → 40×30  (wrap around 10×10×10 box)
 *   eyeL        : 0,32 → 12×10
 *   eyeR        : 14,32→ 12×10
 *   jaw         : 0,44 → 20×8
 *   legs (12p)  : 28,32 → 36×16
 */
@OnlyIn(Dist.CLIENT)
public class ScreetchModel extends EntityModel<ScreetchEntity> {

    private final ModelPart body;
    private final ModelPart eyeL;
    private final ModelPart eyeR;
    private final ModelPart jaw;

    // 3 legs per side, 2 parts each
    private final ModelPart[] legUpperL = new ModelPart[3];
    private final ModelPart[] legLowerL = new ModelPart[3];
    private final ModelPart[] legUpperR = new ModelPart[3];
    private final ModelPart[] legLowerR = new ModelPart[3];

    // ── LayerDefinition factory ───────────────────────────────────────────────

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ── Body ──────────────────────────────────────────────────────────────
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-5, -5, -5, 10, 10, 10),
                PartPose.offset(0, 18, 0));

        // ── Eyes ─────────────────────────────────────────────────────────────
        body.addOrReplaceChild("eyeL",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(-4f, -3f, -5.5f, 3, 2, 1),
                PartPose.ZERO);

        body.addOrReplaceChild("eyeR",
                CubeListBuilder.create()
                        .texOffs(14, 32)
                        .addBox(1f, -3f, -5.5f, 3, 2, 1),
                PartPose.ZERO);

        // ── Jaw (wide open mouth) ─────────────────────────────────────────────
        body.addOrReplaceChild("jaw",
                CubeListBuilder.create()
                        .texOffs(0, 44)
                        .addBox(-4f, 0f, -5.5f, 8, 2, 1),
                PartPose.ZERO);

        // ── Legs (6 total, attached to body) ─────────────────────────────────
        // Each "upper" leg angles upward-outward from body, then "lower" angles back down.
        // offsets along X and slightly different Z for each pair
        float[] legZ = {-3f, 0f, 3f};

        for (int i = 0; i < 3; i++) {
            // Left upper leg
            body.addOrReplaceChild("legUpperL" + i,
                    CubeListBuilder.create()
                            .texOffs(28, 32 + i * 5)
                            .addBox(-5f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(-5, 0, legZ[i],
                            0, 0, (float) Math.toRadians(-45)));

            // Left lower leg
            root.addOrReplaceChild("legLowerL" + i,
                    CubeListBuilder.create()
                            .texOffs(40, 32 + i * 5)
                            .addBox(-5f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(-8.5f, 14.5f, legZ[i],
                            0, 0, (float) Math.toRadians(45)));

            // Right upper leg
            body.addOrReplaceChild("legUpperR" + i,
                    CubeListBuilder.create()
                            .texOffs(28, 47 + i * 5)
                            .addBox(0f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(5, 0, legZ[i],
                            0, 0, (float) Math.toRadians(45)));

            // Right lower leg
            root.addOrReplaceChild("legLowerR" + i,
                    CubeListBuilder.create()
                            .texOffs(40, 47 + i * 5)
                            .addBox(0f, 0f, -1f, 5, 1, 2),
                    PartPose.offsetAndRotation(8.5f, 14.5f, legZ[i],
                            0, 0, (float) Math.toRadians(-45)));
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public ScreetchModel(ModelPart root) {
        this.body = root.getChild("body");
        this.eyeL = body.getChild("eyeL");
        this.eyeR = body.getChild("eyeR");
        this.jaw  = body.getChild("jaw");

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
        // Gentle idle bob for the body
        body.y = 18f + (float) Math.sin(ageInTicks * 0.1f) * 0.5f;

        // Warn animation: as bite timer advances the jaw opens wider and body shakes
        float warnProgress = entity.getBiteTimer() / (float) ScreetchEntity.BITE_TICKS;
        float jawOpen = warnProgress * 0.6f; // max ~34 degrees open
        jaw.xRot = jawOpen;

        // Slight body tremble near bite
        if (warnProgress > 0.7f) {
            float tremble = (float) Math.sin(ageInTicks * 0.8f) * 0.05f * (warnProgress - 0.7f) / 0.3f;
            body.xRot = tremble;
            body.zRot = tremble * 0.7f;
        } else {
            body.xRot = 0;
            body.zRot = 0;
        }

        // Leg wave animation
        for (int i = 0; i < 3; i++) {
            float phase = ageInTicks * 0.15f + i * 1.2f;
            float wave = (float) Math.sin(phase) * 0.3f;

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
        body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        for (int i = 0; i < 3; i++) {
            legLowerL[i].render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            legLowerR[i].render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}
