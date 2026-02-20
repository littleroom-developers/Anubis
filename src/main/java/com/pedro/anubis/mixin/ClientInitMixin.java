package com.pedro.anubis.mixin;

import com.pedro.anubis.AnubisAddon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ClientInitMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onGameLoaded(RunArgs args, CallbackInfo ci) {
        AnubisAddon.LOG.info("Anubis mixin loaded.");
    }
}
