package com.zahrproject.votingmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zahrproject.votingmod.entity.BlackHoleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * No-op renderer for BlackHoleEntity.
 * All visual effects are handled by server-side particles (SQUID_INK, PORTAL, LARGE_SMOKE).
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHoleEntity> {

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHoleEntity entity) {
        // No texture – entity is invisible; visuals come from particles only.
        return new ResourceLocation("votingmod", "textures/entity/black_hole.png");
    }

    @Override
    public void render(BlackHoleEntity entity, float yaw, float partialTick,
                       PoseStack stack, MultiBufferSource buffers, int light) {
        // Intentionally empty – particles provide the visual.
    }
}
