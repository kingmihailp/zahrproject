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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the standard enchantment glint with a lime-green glint when
 * the rendered item carries the Terra Blade enchantment.
 *
 * Uses targets = "..." instead of ItemRenderer.class to avoid compile-time
 * dependency on the client-only class inside the annotation processor.
 */
@Mixin(targets = "net.minecraft.client.renderer.ItemRenderer")
public class ItemRendererMixin {

    // Shadow the two static foil-buffer helpers so we can call them without
    // a direct compile-time reference to ItemRenderer.
    @Shadow
    private static VertexConsumer getFoilBufferDirect(MultiBufferSource bufferSource,
                                                       RenderType renderType,
                                                       boolean isItem,
                                                       boolean isGlint) {
        throw new AssertionError("@Shadow");
    }

    @Shadow
    private static VertexConsumer getFoilBuffer(MultiBufferSource bufferSource,
                                                 RenderType renderType,
                                                 boolean isItem,
                                                 boolean isGlint) {
        throw new AssertionError("@Shadow");
    }

    // ── Thread-local flag ─────────────────────────────────────────────────────

    private static final ThreadLocal<Boolean> TERRA_BLADE_ACTIVE =
            ThreadLocal.withInitial(() -> false);

    // ── Inject: detect Terra Blade at render start / end ─────────────────────

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;" +
                  "Lnet/minecraft/world/item/ItemDisplayContext;Z" +
                  "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                  "Lnet/minecraft/client/renderer/MultiBufferSource;II" +
                  "Lnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD"))
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
        at = @At("RETURN"))
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

    // ── Redirect: getFoilBufferDirect (GUI / flat-lit items) ──────────────────

    @Redirect(
        method = "render(Lnet/minecraft/world/item/ItemStack;" +
                  "Lnet/minecraft/world/item/ItemDisplayContext;Z" +
                  "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                  "Lnet/minecraft/client/renderer/MultiBufferSource;II" +
                  "Lnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/renderer/ItemRenderer;" +
                          "getFoilBufferDirect(" +
                          "Lnet/minecraft/client/renderer/MultiBufferSource;" +
                          "Lnet/minecraft/client/renderer/RenderType;ZZ)" +
                          "Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer redirectFoilBufferDirect(MultiBufferSource bufferSource,
                                                     RenderType renderType,
                                                     boolean isItem,
                                                     boolean isGlint) {
        if (isGlint && TERRA_BLADE_ACTIVE.get()) {
            return VertexMultiConsumer.create(
                    bufferSource.getBuffer(LimeGlintHelper.LIME_GLINT_DIRECT),
                    bufferSource.getBuffer(renderType));
        }
        return getFoilBufferDirect(bufferSource, renderType, isItem, isGlint);
    }

    // ── Redirect: getFoilBuffer (3-D world items) ─────────────────────────────

    @Redirect(
        method = "render(Lnet/minecraft/world/item/ItemStack;" +
                  "Lnet/minecraft/world/item/ItemDisplayContext;Z" +
                  "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                  "Lnet/minecraft/client/renderer/MultiBufferSource;II" +
                  "Lnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/renderer/ItemRenderer;" +
                          "getFoilBuffer(" +
                          "Lnet/minecraft/client/renderer/MultiBufferSource;" +
                          "Lnet/minecraft/client/renderer/RenderType;ZZ)" +
                          "Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer redirectFoilBuffer(MultiBufferSource bufferSource,
                                               RenderType renderType,
                                               boolean isItem,
                                               boolean isGlint) {
        if (isGlint && TERRA_BLADE_ACTIVE.get()) {
            return VertexMultiConsumer.create(
                    bufferSource.getBuffer(LimeGlintHelper.LIME_GLINT_TRANSLUCENT),
                    bufferSource.getBuffer(renderType));
        }
        return getFoilBuffer(bufferSource, renderType, isItem, isGlint);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static boolean hasTerraBladeEnchantment(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasFoil()) return false;
        if (EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.TERRA_BLADE.get(), stack) > 0)
            return true;
        if (stack.is(Items.ENCHANTED_BOOK)) {
            return EnchantmentHelper.deserializeEnchantments(EnchantedBookItem.getEnchantments(stack))
                    .containsKey(ModEnchantments.TERRA_BLADE.get());
        }
        return false;
    }
}
