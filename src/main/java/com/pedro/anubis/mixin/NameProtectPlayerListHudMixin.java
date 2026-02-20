package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.main.NameProtect;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class NameProtectPlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void anubis$onGetPlayerName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        NameProtect nameProtect = Modules.get().get(NameProtect.class);
        if (nameProtect == null || !nameProtect.isActive()) return;

        Text protectedName = nameProtect.getTabNameFromEntry(entry);
        if (protectedName != null) cir.setReturnValue(protectedName);
    }
}
