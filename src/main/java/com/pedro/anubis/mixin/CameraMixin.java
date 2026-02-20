package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.main.AnubisFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "method_19321(Lnet/minecraft/class_1922;Lnet/minecraft/class_1297;ZZF)V", at = @At("TAIL"))
    private void onUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean frontView, float tickDelta, CallbackInfo ci) {
        AnubisFreecam freecam = Modules.get().get(AnubisFreecam.class);
        if (freecam != null && freecam.isActive()) {
            setPos(freecam.getX(tickDelta), freecam.getY(tickDelta), freecam.getZ(tickDelta));
            setRotation(freecam.getYaw(tickDelta), freecam.getPitch(tickDelta));
        }
    }

    @Inject(method = "method_19333()Z", at = @At("HEAD"), cancellable = true)
    private void onIsThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get().isActive(AnubisFreecam.class)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "method_19331()Lnet/minecraft/class_1297;", at = @At("HEAD"), cancellable = true)
    private void onGetFocusedEntity(CallbackInfoReturnable<Entity> cir) {
    }
}
