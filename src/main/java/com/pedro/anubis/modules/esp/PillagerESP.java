package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PillagerESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgESP = settings.createGroup("ESP");
    private final SettingGroup sgTracers = settings.createGroup("Tracers");

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to render pillagers.")
        .defaultValue(128)
        .range(16, 256)
        .sliderRange(16, 256)
        .build()
    );

    private final Setting<Boolean> showCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show pillager count in chat when it changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<NotificationMode> notificationMode = sgGeneral.add(new EnumSetting.Builder<NotificationMode>()
        .name("notification-mode")
        .description("How to notify when pillagers are detected.")
        .defaultValue(NotificationMode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-when-found")
        .description("Automatically toggles the module when pillagers are detected.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Automatically disconnect when pillagers are detected.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> espColor = sgESP.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color of pillager ESP.")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgESP.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the ESP shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> tracersEnabled = sgTracers.add(new BoolSetting.Builder()
        .name("tracers-enabled")
        .description("Enable tracers to pillagers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgTracers.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of tracers.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(tracersEnabled::get)
        .build()
    );

    private final Setting<TracersMode> tracersMode = sgTracers.add(new EnumSetting.Builder<TracersMode>()
        .name("tracers-mode")
        .description("How tracers are rendered.")
        .defaultValue(TracersMode.Line)
        .visible(tracersEnabled::get)
        .build()
    );

    private final List<PillagerEntity> pillagers = new ArrayList<>();
    private final Set<Integer> detectedPillagers = new HashSet<>();
    private int lastPillagerCount = 0;

    public PillagerESP() {
        super(AnubisAddon.ESP, "pillager-esp", "ESP for pillagers with tracers and info display.");
    }

    @Override
    public void onActivate() {
        pillagers.clear();
        detectedPillagers.clear();
        lastPillagerCount = 0;
    }

    @Override
    public void onDeactivate() {
        pillagers.clear();
        detectedPillagers.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || event.renderer == null) return;

        pillagers.clear();
        HashSet<Integer> current = new HashSet<>();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof PillagerEntity p)) continue;
            if (mc.player.distanceTo(p) > maxDistance.get()) continue;
            pillagers.add(p);
            current.add(p.getId());
        }

        if (showCount.get() && pillagers.size() != lastPillagerCount) {
            if (!pillagers.isEmpty()) notifyFoundCount(pillagers.size());
            lastPillagerCount = pillagers.size();
        }

        if (!current.isEmpty() && !current.equals(detectedPillagers)) {
            HashSet<Integer> newOnes = new HashSet<>(current);
            newOnes.removeAll(detectedPillagers);
            if (!newOnes.isEmpty()) {
                detectedPillagers.addAll(newOnes);
                handlePillagerDetection(pillagers.size());
            }
        } else if (current.isEmpty()) {
            detectedPillagers.clear();
        }

        for (PillagerEntity p : pillagers) {
            if (p == null || !p.isAlive()) continue;
            renderESP(p.getBoundingBox(), event);
            if (tracersEnabled.get()) renderTracers(p, event);
        }
    }

    private void notifyFoundCount(int count) {
        String title = "PillagerESP";
        String msg = "Found " + count + " pillager(s) nearby";
        switch (notificationMode.get()) {
            case Chat -> info("%s: %s", title, msg);
            case Toast -> info("%s: %s", title, msg);
            case Both -> {
                info("%s: %s", title, msg);
                info("%s: %s", title, msg);
            }
        }
    }

    private void handlePillagerDetection(int pillagerCount) {
        if (toggleOnFind.get()) toggle();
        if (enableDisconnect.get()) {
            String msg = pillagerCount == 1 ? "Pillager detected!" : (pillagerCount + " pillagers detected!");
            disconnectFromServer(msg);
        }
    }

    private void disconnectFromServer(String reason) {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            info("Disconnected from server - " + reason);
        }
    }

    private void renderESP(Box box, Render3DEvent event) {
        Color c = new Color(espColor.get());
        event.renderer.box(box, c, c, shapeMode.get(), 0);
    }

    private void renderTracers(PillagerEntity p, Render3DEvent event) {
        if (mc.player == null) return;
        Color c = new Color(tracerColor.get());
        Vec3d start = RenderUtils.center != null ? RenderUtils.center : mc.gameRenderer.getCamera().getPos();
        Vec3d center = p.getPos().add(0, p.getHeight() / 2.0, 0);

        switch (tracersMode.get()) {
            case Line -> event.renderer.line(start.x, start.y, start.z, center.x, center.y, center.z, c);
            case Dot -> {
                Box dot = new Box(center.x - 0.1, center.y - 0.1, center.z - 0.1, center.x + 0.1, center.y + 0.1, center.z + 0.1);
                event.renderer.box(dot, c, c, ShapeMode.Both, 0);
            }
            case Both -> {
                event.renderer.line(start.x, start.y, start.z, center.x, center.y, center.z, c);
                Box dot = new Box(center.x - 0.1, center.y - 0.1, center.z - 0.1, center.x + 0.1, center.y + 0.1, center.z + 0.1);
                event.renderer.box(dot, c, c, ShapeMode.Both, 0);
            }
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(pillagers.size());
    }

    public enum NotificationMode {
        Chat, Toast, Both
    }

    public enum TracersMode {
        Line, Dot, Both
    }
}
