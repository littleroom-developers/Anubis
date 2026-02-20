package com.pedro.anubis.modules.main;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

public class HomeReset extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
            .name("chat-feedback")
            .description("Show chat messages when running commands.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> homeSlot = sgGeneral.add(new IntSetting.Builder()
            .name("home-slot")
            .description("Which home slot to reset (1-5).")
            .defaultValue(1)
            .range(1, 5)
            .sliderRange(1, 5)
            .build());

    private int pendingSlot = -1;
    private int waitTicks;

    public HomeReset() {
        super(AnubisAddon.MAIN, "home-reset", "Automatically runs /delhome then /sethome for a selected slot.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            error("You must be in-game to use this module.");
            toggle();
            return;
        }

        pendingSlot = homeSlot.get();
        waitTicks = 30; // ~100ms at 20 TPS.
        ChatUtils.sendPlayerMsg("/delhome " + pendingSlot);

        if (chatFeedback.get()) {
            info("Resetting home slot " + pendingSlot + "...");
        }
    }

    @Override
    public void onDeactivate() {
        pendingSlot = -1;
        waitTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pendingSlot < 1)
            return;
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        if (waitTicks-- > 0)
            return;

        ChatUtils.sendPlayerMsg("/sethome " + pendingSlot);

        if (chatFeedback.get()) {
            info("Home slot " + pendingSlot + " was reset.");
        }

        toggle();
    }
}
