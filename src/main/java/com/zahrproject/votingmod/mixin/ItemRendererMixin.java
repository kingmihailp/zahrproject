package com.zahrproject.votingmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.zahrproject.votingmod.client.LimeGlintHelper;
import com.zahrproject.votingmod.enchantments.ModEnchantments;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the standard enchantment glint with a lime-green glint when
 * the rendered item carries the Terra Blade enchantment.
 *
 * remap = false is used because the Mixin AP cannot resolve SRG mappings for
 * Mojang-named methods. The method names here are the actual runtime names in
 * the production Forge 1.20.1 jar.
 *
 * @Redirect into render() was previously used but had 0 injection points:
 * getFoilBufferDirect/getFoilBuffer are not called directly from render() but
 * from a private helper. We now @Inject directly into those static methods.
 */
@Mixin(targets = "net.minecraft.client.renderer.ItemRenderer")
public class ItemRendererMixin {

    // ── Thread-local flag set while render() processes a Terra Blade item ─────

    private static final ThreadLocal<Boolean> TERRA_BLADE_ACTIVE =
            ThreadLocal.withInitial(() -> false);

    // ── Inject: set/clear flag around render() ────────────────────────────────

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;" +
                  "Lnet/minecraft/world/item/ItemDisplayContext;Z" +
                  "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                  "Lnet/minecraft/client/renderer/MultiBufferSource;II" +
                  "Lnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD"),
        remap = false)
    private void onRenderHead(ItemStack stack,
                               ItemDisplayContext displayContext,
                               boolean leftHand,
                               PoseStack poseStack,
                               MultiBufferSource bufferSource,
                               int combinedLight,
                               int combinedOverlay,
                               BakedModel model,
                               CallbackInfo ci) {
        TERRA_BLADE_ACTIVE.set(hasTerraBladeEnchantment(stack));
    }

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;" +
                  "Lnet/minecraft/world/item/ItemDisplayContext;Z" +
                  "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                  "Lnet/minecraft/client/renderer/MultiBufferSource;II" +
                  "Lnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("RETURN"),
        remap = false)
    private void onRenderReturn(ItemStack stack,
                                 ItemDisplayContext displayContext,
                                 boolean leftHand,
                                 PoseStack poseStack,
                                 MultiBufferSource bufferSource,
                                 int combinedLight,
                                 int combinedOverlay,
                                 BakedModel model,
                                 CallbackInfo ci) {
        TERRA_BLADE_ACTIVE.set(false);
    }

    // ── Inject into getFoilBufferDirect (GUI / flat-lit context) ──────────────

    @Inject(
        method = "getFoilBufferDirect(" +
                  "Lnet/minecraft/client/renderer/MultiBufferSource;" +
                  "Lnet/minecraft/client/renderer/RenderType;ZZ)" +
                  "Lcom/mojang/blaze3d/vertex/VertexConsumer;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void injectFoilBufferDirect(MultiBufferSource bufferSource,
                                                RenderType renderType,
                                                boolean isItem,
                                                boolean isGlint,
                                                CallbackInfoReturnable<VertexConsumer> cir) {
        if (isGlint && TERRA_BLADE_ACTIVE.get()) {
            cir.setReturnValue(VertexMultiConsumer.create(
                    bufferSource.getBuffer(LimeGlintHelper.LIME_GLINT_DIRECT),
                    bufferSource.getBuffer(renderType)));
        }
    }

    // ── Inject into getFoilBuffer (3-D world context) ─────────────────────────

    @Inject(
        method = "getFoilBuffer(" +
                  "Lnet/minecraft/client/renderer/MultiBufferSource;" +
                  "Lnet/minecraft/client/renderer/RenderType;ZZ)" +
                  "Lcom/mojang/blaze3d/vertex/VertexConsumer;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void injectFoilBuffer(MultiBufferSource bufferSource,
                                          RenderType renderType,
                                          boolean isItem,
                                          boolean isGlint,
                                          CallbackInfoReturnable<VertexConsumer> cir) {
        if (isGlint && TERRA_BLADE_ACTIVE.get()) {
            cir.setReturnValue(VertexMultiConsumer.create(
                    bufferSource.getBuffer(LimeGlintHelper.LIME_GLINT_TRANSLUCENT),
                    bufferSource.getBuffer(renderType)));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static boolean hasTerraBladeEnchantment(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasFoil()) return false;
        if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.TERRA_BLADE.get(), stack) > 0)
            return true;
        if (stack.is(Items.ENCHANTED_BOOK)) {
            return EnchantmentHelper.deserializeEnchantments(EnchantedBookItem.getEnchantments(stack))
                    .containsKey(ModEnchantments.TERRA_BLADE.get());
        }
        return false;
    }
}
