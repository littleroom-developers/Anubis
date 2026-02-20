package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class SkeletonESP
extends Module {
    private final SettingGroup sgGeneral;
    private final Setting<SettingColor> color;
    private final Setting<Boolean> distanceColors;
    private final Setting<Double> verticalOffset;
    private final Setting<Double> forwardOffset;
    private final Setting<Double> horizontalOffset;

    public SkeletonESP() {
        super(AnubisAddon.ESP, "skeleton-esp", "array player skeletons inside players with correct offsets & rotation (no legs).");
        this.sgGeneral = this.settings.createGroup("General");
        this.color = this.sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("color")).description("Color of index skeleton ESP")).defaultValue(new SettingColor(255, 255, 255, 255)).build());
        this.distanceColors = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("getDistance-colors")).description("Change skeleton color based on getDistance")).defaultValue(false)).build());
        this.verticalOffset = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("vertical-offset")).description("Fixed vertical offset for skeleton placement.")).defaultValue(1.35).min(1.0).max(1.6).sliderRange(1.0, 1.6).build());
        this.forwardOffset = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("forward-offset")).description("Forward/back offset of skeleton from chest center.")).defaultValue(0.0).min(-0.3).max(0.3).sliderRange(-0.3, 0.3).build());
        this.horizontalOffset = this.sgGeneral.add(((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("horizontal-offset")).description("Horizontal width of shoulders/arms.")).defaultValue(0.25).min(0.0).max(0.5).sliderRange(0.0, 0.5).build());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        List<AbstractClientPlayerEntity> players = this.mc.world.getPlayers();
        for (AbstractClientPlayerEntity player : players) {
            if (this.mc.options.getPerspective() == Perspective.FIRST_PERSON && player == this.mc.player) continue;
            Vec3d basePos = player.getLerpedPos(event.tickDelta);
            Color skeletonColor = (Boolean)this.distanceColors.get() != false ? this.getColorFromDistance(basePos) : new Color((Color)this.color.get());
            double yawRad = Math.toRadians(-player.bodyYaw);
            Vec3d chestBase = basePos.add(0.0, ((Double)this.verticalOffset.get()).doubleValue(), 0.0);
            if (player.isSneaking()) {
                chestBase = chestBase.add(0.0, -0.2, 0.0);
            }
            Vec3d forwardVec = new Vec3d(0.0, 0.0, ((Double)this.forwardOffset.get()).doubleValue()).rotateY((float)yawRad);
            chestBase = chestBase.add(forwardVec);
            Vec3d leftShoulder = chestBase.add(new Vec3d(-((Double)this.horizontalOffset.get()).doubleValue(), 0.0, 0.0).rotateY((float)yawRad));
            Vec3d rightShoulder = chestBase.add(new Vec3d(((Double)this.horizontalOffset.get()).doubleValue(), 0.0, 0.0).rotateY((float)yawRad));
            Vec3d leftArmEnd = leftShoulder.add(0.0, -0.6, 0.0);
            Vec3d rightArmEnd = rightShoulder.add(0.0, -0.6, 0.0);
            Vec3d spineStart = basePos.add(forwardVec);
            Vec3d spineEnd = chestBase;
            Vec3d headTop = chestBase.add(0.0, 0.25, 0.0);
            event.renderer.line(spineStart.x, spineStart.y, spineStart.z, spineEnd.x, spineEnd.y, spineEnd.z, skeletonColor);
            event.renderer.line(leftShoulder.x, leftShoulder.y, leftShoulder.z, rightShoulder.x, rightShoulder.y, rightShoulder.z, skeletonColor);
            event.renderer.line(leftShoulder.x, leftShoulder.y, leftShoulder.z, leftArmEnd.x, leftArmEnd.y, leftArmEnd.z, skeletonColor);
            event.renderer.line(rightShoulder.x, rightShoulder.y, rightShoulder.z, rightArmEnd.x, rightArmEnd.y, rightArmEnd.z, skeletonColor);
            event.renderer.line(spineEnd.x, spineEnd.y, spineEnd.z, headTop.x, headTop.y, headTop.z, skeletonColor);
        }
    }

    private Color getColorFromDistance(Vec3d pos) {
        int g;
        int r;
        double getDistance = this.mc.player.getPos().distanceTo(pos);
        double percent = Math.min(1.0, getDistance / 60.0);
        if (percent < 0.33) {
            r = (int)(percent / 0.33 * 255.0);
            g = 255;
        } else if (percent < 0.66) {
            r = 255;
            g = 255 - (int)((percent - 0.33) / 0.33 * 90.0);
        } else {
            r = 255;
            g = 165 - (int)((percent - 0.66) / 0.34 * 165.0);
        }
        return new Color(r, g, 0, 255);
    }
}
