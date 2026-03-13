package com.zahrproject.votingmod.mixin;

import com.zahrproject.votingmod.client.JebRainbowLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into every {@link LivingEntityRenderer} constructor so that
 * {@link JebRainbowLayer} is automatically added to <em>all</em> living-entity
 * renderers — zombies, creepers, players, boats that are living entities, etc.
 *
 * <p>The layer itself is a no-op unless the entity's custom name is {@code "_jeb"},
 * so there is no performance cost for ordinary entities.
 *
 * <p>{@code remap = false} because the constructor ({@code <init>}) is never
 * obfuscated, and we want to avoid accidental remapping of type descriptors.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.renderer.entity.LivingEntityRenderer")
public abstract class LivingEntityRendererMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void votingmod$addJebRainbowLayer(
            EntityRendererProvider.Context ctx,
            EntityModel<?> model,
            float shadowRadius,
            CallbackInfo ci) {

        LivingEntityRenderer self = (LivingEntityRenderer) (Object) this;
        self.addLayer(new JebRainbowLayer<>(self));
    }
}
