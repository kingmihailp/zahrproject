package com.zahrproject.votingmod.mixin;

import com.zahrproject.votingmod.handler.FishingTrickHandler;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    @Shadow(remap = false)
    private int timeUntilLured;

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void onTick(CallbackInfo ci) {
        if (FishingTrickHandler.isActive() && timeUntilLured > 1) {
            timeUntilLured = 1;
        }
    }
}
