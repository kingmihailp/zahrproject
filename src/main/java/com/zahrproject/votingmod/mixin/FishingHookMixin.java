package com.zahrproject.votingmod.mixin;

import com.zahrproject.votingmod.handler.FishingTrickHandler;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets timeUntilLured = 1 every tick while "Рыбацкая хитрость" is active,
 * making the fishing hook bite almost instantly.
 *
 * Uses @Shadow on the non-final private int — no @Mutable needed (no final).
 * No @Accessor interface required, so no bootstrap crash.
 */
@Mixin(FishingHook.class)
public class FishingHookMixin {

    @Shadow private int timeUntilLured;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        FishingHook self = (FishingHook)(Object)this;
        if (!self.level().isClientSide() && FishingTrickHandler.isActive()) {
            if (this.timeUntilLured > 1) {
                this.timeUntilLured = 1;
            }
        }
    }
}
