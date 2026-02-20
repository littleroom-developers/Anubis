package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.main.AnubisFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(
        method = "method_3950(Lnet/minecraft/class_1297;Lnet/minecraft/class_4604;DDD)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onShouldRender(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        AnubisFreecam freecam = Modules.get().get(AnubisFreecam.class);
        if (freecam != null && freecam.isActive() && entity == MinecraftClient.getInstance().player) {
            cir.setReturnValue(true);
        }
    }
}
