package com.pedro.anubis.modules.main;

import com.mojang.authlib.GameProfile;
import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class NameProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHiding = settings.createGroup("UI Hiding");

    public final Setting<Boolean> hideSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-self")
        .description("Protect your own identity.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> replaceName = sgGeneral.add(new BoolSetting.Builder()
        .name("replace-name")
        .description("Replace the name instead of hiding it entirely.")
        .defaultValue(true)
        .build()
    );

    public final Setting<String> customName = sgGeneral.add(new StringSetting.Builder()
        .name("custom-name")
        .description("The name to display instead of the real one.")
        .defaultValue("Protected")
        .visible(replaceName::get)
        .build()
    );

    public final Setting<Boolean> protectTab = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-tab")
        .description("Apply name protection to the Tab list.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> protectChat = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-chat")
        .description("Replace names in the chat window.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> protectOverlay = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-overlay")
        .description("Replace names in the text above your hotbar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Distance to protect player identities in the 3D world.")
        .defaultValue(64.0)
        .min(0.0)
        .sliderMax(128.0)
        .build()
    );

    public final Setting<Boolean> hideFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-friends")
        .description("Whether to protect friends' identities as well.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> hideSkins = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-skins")
        .description("Replaces player skins with default Steve/Alex skins.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> hideHotbar = sgHiding.add(new BoolSetting.Builder()
        .name("hide-hotbar")
        .description("Completely hides the hotbar via Mixin.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> hideExp = sgHiding.add(new BoolSetting.Builder()
        .name("hide-experience")
        .description("Hides the experience bar.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> hideArmor = sgHiding.add(new BoolSetting.Builder()
        .name("hide-armor")
        .description("Hides the armor bar above the health.")
        .defaultValue(false)
        .build()
    );

    public NameProtect() {
        super(AnubisAddon.MAIN, "nameprotect", "Global Privacy: Protects everyone in world, tab, and chat.");
    }

    public String replaceNameInText(String input) {
        if (!isActive() || input == null || input.isEmpty()) return input;

        String result = input;
        String replacement = customName.get();
        String selfName = mc.getSession().getUsername();

        if (hideSelf.get()) {
            result = result.replace(selfName, replacement);
        }

        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().getName();
                if (name == null || name.isEmpty() || name.equals(selfName)) continue;
                if (Friends.get().isFriend(entry) && !hideFriends.get()) continue;
                result = result.replace(name, replacement);
            }
        }

        return result;
    }

    public boolean shouldProtect(PlayerEntity player) {
        if (!isActive() || mc.player == null || player == null) return false;

        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            boolean isBedrock = player.getUuidAsString().startsWith("00000000-0000-0000");
            if (entry == null && !isBedrock) return false;
        }

        if (player == mc.player) return hideSelf.get();
        if (Friends.get().isFriend(player)) return hideFriends.get();
        return mc.player.distanceTo((Entity) player) <= range.get();
    }

    public boolean shouldHideSkin(PlayerEntity player) {
        return isActive() && hideSkins.get() && shouldProtect(player);
    }

    public Text getTabNameFromEntry(PlayerListEntry entry) {
        if (!isActive() || !protectTab.get() || entry == null) return null;

        GameProfile profile = entry.getProfile();
        String selfName = mc.getSession().getUsername();

        if (hideSelf.get() && profile.getName().equals(selfName)) {
            return Text.literal(customName.get());
        }

        if (Friends.get().isFriend(entry)) {
            return hideFriends.get() ? Text.literal(customName.get()) : null;
        }

        return Text.literal(customName.get());
    }

    public Text getRenderText(PlayerEntity player) {
        if (shouldProtect(player)) {
            return replaceName.get() ? Text.literal(customName.get()) : null;
        }
        return player.getDisplayName();
    }
}
