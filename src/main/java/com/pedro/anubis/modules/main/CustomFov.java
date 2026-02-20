package com.pedro.anubis.modules.main;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

public final class CustomFov extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> fov = sgGeneral.add(new IntSetting.Builder()
        .name("fov")
        .description("Field of view in degrees.")
        .defaultValue(110)
        .range(30, 160)
        .sliderRange(30, 160)
        .build()
    );

    public CustomFov() {
        super(AnubisAddon.MAIN, "custom-fov", "Changes your field of view.");
    }

    public static CustomFov get() {
        return Modules.get().get(CustomFov.class);
    }

    @EventHandler
    private void onGetFov(GetFovEvent event) {
        event.fov = fov.get();
    }
}
