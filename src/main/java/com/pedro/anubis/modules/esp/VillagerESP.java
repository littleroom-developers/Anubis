package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class VillagerESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<DetectionMode> detectionMode = sgGeneral.add(new EnumSetting.Builder<DetectionMode>()
        .name("detection-mode")
        .description("What type of villagers to detect.")
        .defaultValue(DetectionMode.Both)
        .build()
    );

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draw tracer lines to villagers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> villagerTracerColor = sgRender.add(new ColorSetting.Builder()
        .name("villager-tracer-color")
        .description("Tracer color for regular villagers.")
        .defaultValue(new SettingColor(0, 255, 0, 127))
        .visible(() -> showTracers.get() && (detectionMode.get() == DetectionMode.Villagers || detectionMode.get() == DetectionMode.Both))
        .build()
    );

    private final Setting<SettingColor> zombieVillagerTracerColor = sgRender.add(new ColorSetting.Builder()
        .name("zombie-villager-tracer-color")
        .description("Tracer color for zombie villagers.")
        .defaultValue(new SettingColor(255, 0, 0, 127))
        .visible(() -> showTracers.get() && (detectionMode.get() == DetectionMode.ZombieVillagers || detectionMode.get() == DetectionMode.Both))
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Automatically disconnect when villagers are detected.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when villagers are detected.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-when-found")
        .description("Automatically toggles the module when villagers are detected.")
        .defaultValue(false)
        .build()
    );

    private final Set<Integer> detected = new HashSet<>();

    public VillagerESP() {
        super(AnubisAddon.ESP, "villager-esp", "Detects villagers and zombie villagers in the world.");
    }

    @Override
    public void onActivate() {
        detected.clear();
    }

    @Override
    public void onDeactivate() {
        detected.clear();
    }

    @Override
    public String getInfoString() {
        return detected.isEmpty() ? null : String.valueOf(detected.size());
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        DetectionMode mode = detectionMode.get();
        Box searchBox = mc.player.getBoundingBox().expand(4096);

        Set<Integer> current = new HashSet<>();
        int villagerCount = 0;
        int zombieCount = 0;

        Vec3d start = RenderUtils.center != null ? RenderUtils.center : mc.gameRenderer.getCamera().getPos();

        boolean tracers = showTracers.get();
        Color villColor = tracers ? new Color(villagerTracerColor.get()) : null;
        Color zomColor = tracers ? new Color(zombieVillagerTracerColor.get()) : null;

        if (mode == DetectionMode.Villagers || mode == DetectionMode.Both) {
            for (VillagerEntity v : mc.world.getEntitiesByType(TypeFilter.instanceOf(VillagerEntity.class), searchBox, e -> true)) {
                villagerCount++;
                current.add(v.getId());

                if (tracers) {
                    double x = MathHelper.lerp(event.tickDelta, v.prevX, v.getX());
                    double y = MathHelper.lerp(event.tickDelta, v.prevY, v.getY());
                    double z = MathHelper.lerp(event.tickDelta, v.prevZ, v.getZ());
                    y += v.getBoundingBox().getLengthY() / 2.0;
                    event.renderer.line(start.x, start.y, start.z, x, y, z, villColor);
                }
            }
        }

        if (mode == DetectionMode.ZombieVillagers || mode == DetectionMode.Both) {
            for (ZombieVillagerEntity z : mc.world.getEntitiesByType(TypeFilter.instanceOf(ZombieVillagerEntity.class), searchBox, e -> true)) {
                zombieCount++;
                current.add(z.getId());

                if (tracers) {
                    double x = MathHelper.lerp(event.tickDelta, z.prevX, z.getX());
                    double y = MathHelper.lerp(event.tickDelta, z.prevY, z.getY());
                    double zz = MathHelper.lerp(event.tickDelta, z.prevZ, z.getZ());
                    y += z.getBoundingBox().getLengthY() / 2.0;
                    event.renderer.line(start.x, start.y, start.z, x, y, zz, zomColor);
                }
            }
        }

        if (!current.isEmpty() && !current.equals(detected)) {
            Set<Integer> newOnes = new HashSet<>(current);
            newOnes.removeAll(detected);

            if (!newOnes.isEmpty()) {
                detected.addAll(newOnes);
                handleDetection(villagerCount, zombieCount);
            }
        } else if (current.isEmpty()) {
            detected.clear();
        }
    }

    private void handleDetection(int villagerCount, int zombieVillagerCount) {
        String title = "Villager Alert";
        String message = buildDetectionMessage(villagerCount, zombieVillagerCount);

        switch (notificationMode.get()) {
            case Chat -> info("(highlight)%s", message);
            case Toast -> info("%s: %s", title, message);
            case Both -> {
                info("(highlight)%s", message);
                info("%s: %s", title, message);
            }
        }

        if (toggleOnFind.get()) toggle();
        if (enableDisconnect.get()) disconnectFromServer(message);
    }

    private String buildDetectionMessage(int villagerCount, int zombieVillagerCount) {
        DetectionMode mode = detectionMode.get();

        if (mode == DetectionMode.Villagers) {
            return villagerCount == 1 ? "Villager detected!" : String.format("%d villagers detected!", villagerCount);
        }

        if (mode == DetectionMode.ZombieVillagers) {
            return zombieVillagerCount == 1 ? "Zombie villager detected!" : String.format("%d zombie villagers detected!", zombieVillagerCount);
        }

        if (villagerCount > 0 && zombieVillagerCount > 0) {
            return String.format("%d villagers and %d zombie villagers detected!", villagerCount, zombieVillagerCount);
        }
        if (villagerCount > 0) {
            return villagerCount == 1 ? "Villager detected!" : String.format("%d villagers detected!", villagerCount);
        }
        return zombieVillagerCount == 1 ? "Zombie villager detected!" : String.format("%d zombie villagers detected!", zombieVillagerCount);
    }

    private void disconnectFromServer(String reason) {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            info("Disconnected from server - %s", reason);
        }
    }

    public enum DetectionMode {
        Villagers("Villagers"),
        ZombieVillagers("Zombie Villagers"),
        Both("Both");

        private final String name;

        DetectionMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
