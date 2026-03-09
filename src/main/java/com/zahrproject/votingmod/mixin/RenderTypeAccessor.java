package com.zahrproject.votingmod.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the package-private {@code RenderType.create()} static method so that
 * {@link com.zahrproject.votingmod.client.LimeGlintHelper} can register custom
 * render types without an Access Transformer or reflection.
 */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {

    @Invoker(value = "create", remap = false)
    static RenderType invokeCreate(String name,
                                   VertexFormat format,
                                   VertexFormat.Mode mode,
                                   int bufferSize,
                                   RenderType.CompositeState state) {
        throw new AssertionError("Mixin @Invoker not applied");
    }
}
