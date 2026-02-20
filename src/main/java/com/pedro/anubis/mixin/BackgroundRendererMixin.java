package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.main.AnubisFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
    @Inject(
        method = "method_3211(Lnet/minecraft/class_4184;Lnet/minecraft/class_758$class_4596;Lorg/joml/Vector4f;FZF)Lnet/minecraft/class_9958;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void onApplyFog(
        Camera camera,
        BackgroundRenderer.FogType fogType,
        Vector4f color,
        float viewDistance,
        boolean thickFog,
        float tickDelta,
        CallbackInfoReturnable<Fog> cir
    ) {
        if (Modules.get().isActive(AnubisFreecam.class)) {
            cir.setReturnValue(new Fog(
                viewDistance * 2.0f,
                viewDistance * 3.0f,
                FogShape.CYLINDER,
                color.x,
                color.y,
                color.z,
                0.0f
            ));
        }
    }
}
