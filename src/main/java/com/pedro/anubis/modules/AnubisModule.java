package com.pedro.anubis.modules;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AnubisModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Marker size.")
        .defaultValue(2.0d)
        .range(0.5d, 10.0d)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("Marker color.")
        .defaultValue(Color.MAGENTA)
        .build()
    );

    public AnubisModule() {
        super(AnubisAddon.ESP, "anubis-origin", "Highlights world origin with Anubis marker.");
    }
    
    @EventHandler
    private void onRender3d(Render3DEvent event) {  
        Box marker = new Box(BlockPos.ORIGIN);
        marker = marker.stretch(
            scale.get() * marker.getLengthX(),
            scale.get() * marker.getLengthY(),
            scale.get() * marker.getLengthZ()
        );

        event.renderer.box(marker, color.get(), color.get(), ShapeMode.Both, 0);
    }
}
