package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.main.NameProtect;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudNameProtectMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void anubis$onRenderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NameProtect module = Modules.get().get(NameProtect.class);
        if (module != null && module.isActive() && module.hideHotbar.get()) ci.cancel();
    }

    @Inject(method = "renderExperienceBar(Lnet/minecraft/client/gui/DrawContext;I)V", at = @At("HEAD"), cancellable = true)
    private void anubis$onRenderExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        NameProtect module = Modules.get().get(NameProtect.class);
        if (module != null && module.isActive() && module.hideExp.get()) ci.cancel();
    }

    @Inject(method = "renderArmor(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIII)V", at = @At("HEAD"), cancellable = true)
    private static void anubis$onRenderArmor(DrawContext context, PlayerEntity player, int y, int lines, int lineHeight, int x, CallbackInfo ci) {
        NameProtect module = Modules.get().get(NameProtect.class);
        if (module != null && module.isActive() && module.hideArmor.get()) ci.cancel();
    }

    @Inject(method = "setOverlayMessage(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
    private void anubis$onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        NameProtect module = Modules.get().get(NameProtect.class);
        if (module == null || !module.isActive() || !module.protectOverlay.get() || message == null) return;

        String original = message.getString();
        String replaced = module.replaceNameInText(original);
        if (original.equals(replaced)) return;

        client.inGameHud.setOverlayMessage(Text.literal(replaced), tinted);
        ci.cancel();
    }
}
