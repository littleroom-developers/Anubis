package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.main.AnubisFreecam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Inject(method = "method_1606(D)V", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        AnubisFreecam freecam = Modules.get().get(AnubisFreecam.class);
        if (freecam != null && freecam.isActive()) {
            freecam.changeLookDirection(cursorDeltaX, cursorDeltaY);
            cursorDeltaX = 0.0;
            cursorDeltaY = 0.0;
            ci.cancel();
        }
    }
}
