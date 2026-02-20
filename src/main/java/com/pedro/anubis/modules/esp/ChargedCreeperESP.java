package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ChargedCreeperESP extends Module {
    private final SettingGroup sgRender = settings.createGroup("Rendering");

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the ESP box.")
        .defaultValue(new SettingColor(0, 255, 0, 200))
        .build()
    );

    private final Setting<RenderMode> mode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("mode")
        .description("How the ESP should be rendered.")
        .defaultValue(RenderMode.Both)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers from the player to charged creepers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Tracer color.")
        .defaultValue(new SettingColor(0, 255, 0, 200))
        .visible(tracers::get)
        .build()
    );

    public ChargedCreeperESP() {
        super(AnubisAddon.ESP, "charged-creeper-esp", "Highlights charged creepers.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Color espColor = new Color(color.get());
        ShapeMode shapeMode = switch (mode.get()) {
            case Box -> ShapeMode.Sides;
            case Lines -> ShapeMode.Lines;
            case Both -> ShapeMode.Both;
        };

        Vec3d tracerStart = RenderUtils.center != null ? RenderUtils.center : mc.gameRenderer.getCamera().getPos();
        Color lineColor = tracers.get() ? new Color(tracerColor.get()) : null;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof CreeperEntity creeper)) continue;
            if (!creeper.isCharged()) continue;

            Box box = creeper.getBoundingBox();
            event.renderer.box(box, espColor, espColor, shapeMode, 0);

            if (lineColor != null) {
                double x = MathHelper.lerp(event.tickDelta, creeper.prevX, creeper.getX());
                double y = MathHelper.lerp(event.tickDelta, creeper.prevY, creeper.getY());
                double z = MathHelper.lerp(event.tickDelta, creeper.prevZ, creeper.getZ());
                y += creeper.getBoundingBox().getLengthY() / 2.0;
                event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z, x, y, z, lineColor);
            }
        }
    }

    public enum RenderMode {
        Lines,
        Box,
        Both
    }
}
