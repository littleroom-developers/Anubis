package com.pedro.anubis.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface IMinecraftClientAccessor {
    @Mutable
    @Accessor("attackCooldown")
    void setAttackCooldown(int value);

    @Mutable
    @Accessor("itemUseCooldown")
    void setItemUseCooldown(int value);

    @Accessor("session")
    Session getSession();

    @Mutable
    @Accessor("session")
    void setSession(Session value);

    @Accessor("profileKeys")
    ProfileKeys getProfileKeys();

    @Mutable
    @Accessor("profileKeys")
    void setProfileKeys(ProfileKeys value);

    @Accessor("userApiService")
    UserApiService getUserApiService();

    @Mutable
    @Accessor("userApiService")
    void setUserApiService(UserApiService value);

    @Invoker("doAttack")
    boolean invokeDoAttack();

    @Invoker("doItemUse")
    void invokeDoItemUse();
}
