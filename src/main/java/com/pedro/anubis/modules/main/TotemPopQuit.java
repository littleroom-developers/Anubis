package com.pedro.anubis.modules.main;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class TotemPopQuit extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> notification = sgGeneral.add(new BoolSetting.Builder()
        .name("notification")
        .description("Notifies you in chat when you get disconnected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> disconnectMessage = sgGeneral.add(new StringSetting.Builder()
        .name("disconnect-message")
        .description("Message to display on the disconnect screen.")
        .defaultValue("Totem Pop Detected!")
        .build()
    );

    public TotemPopQuit() {
        super(AnubisAddon.MAIN, "TotemPopQuit", "Disconnects when your totem pops.");
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket)) return;

        EntityStatusS2CPacket packet = (EntityStatusS2CPacket) event.packet;
        if (packet.getStatus() != 35) return;

        Entity entity = packet.getEntity(mc.world);
        if (entity == null || entity != mc.player) return;

        if (notification.get()) {
            ChatUtils.info("TotemPopQuit: Disconnecting due to totem pop.");
        }

        mc.getNetworkHandler().getConnection().disconnect(net.minecraft.text.Text.of(disconnectMessage.get()));
        toggle();
    }
}
