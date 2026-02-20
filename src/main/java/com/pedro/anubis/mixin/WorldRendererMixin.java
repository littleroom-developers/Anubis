package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.main.AnubisFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.WorldRenderer;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "method_40050(Lnet/minecraft/class_2338;)Z", at = @At("HEAD"), cancellable = true)
    private void onIsRenderingReady(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get().isActive(AnubisFreecam.class)) {
            cir.setReturnValue(true);
        }
    }
}
