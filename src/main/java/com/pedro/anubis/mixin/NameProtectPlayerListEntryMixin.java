package com.pedro.anubis.mixin;

import com.mojang.authlib.GameProfile;
import com.pedro.anubis.modules.main.NameProtect;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public class NameProtectPlayerListEntryMixin {
    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void anubis$onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        NameProtect module = Modules.get().get(NameProtect.class);
        PlayerListEntry entry = (PlayerListEntry) (Object) this;

        if (module == null || !module.isActive() || !module.hideSkins.get()) return;

        boolean shouldProtect = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) return;

        String selfName = mc.getSession().getUsername();
        GameProfile profile = entry.getProfile();
        if (profile == null) return;

        if (profile.getName() != null && profile.getName().equals(selfName)) {
            shouldProtect = module.hideSelf.get();
        } else if (mc.world != null) {
            PlayerEntity player = mc.world.getPlayerByUuid(profile.getId());
            shouldProtect = player != null ? module.shouldProtect(player) : true;
        }

        if (shouldProtect) cir.setReturnValue(DefaultSkinHelper.getSkinTextures(profile));
    }
}
