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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class WanderingLamaESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draw tracer lines to wandering trader llamas.")
        .defaultValue(true)
        .build()
    );

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the tracer lines.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 165, 0, 127))
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Automatically disconnect when wandering trader llamas are detected.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when wandering trader llamas are detected.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-when-found")
        .description("Automatically toggles the module when a wandering trader llama is detected.")
        .defaultValue(false)
        .build()
    );

    private final Set<Integer> detectedLlamas = new HashSet<>();

    public WanderingLamaESP() {
        super(AnubisAddon.ESP, "wandering-lama-esp", "Detects wandering trader llamas in the world.");
    }

    @Override
    public void onActivate() {
        detectedLlamas.clear();
    }

    @Override
    public void onDeactivate() {
        detectedLlamas.clear();
    }

    @Override
    public String getInfoString() {
        return detectedLlamas.isEmpty() ? null : String.valueOf(detectedLlamas.size());
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d tracerStart = RenderUtils.center != null ? RenderUtils.center : mc.gameRenderer.getCamera().getPos();
        Set<Integer> currentLlamas = new HashSet<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof TraderLlamaEntity)) continue;

            currentLlamas.add(entity.getId());

            if (showTracers.get()) {
                double x = MathHelper.lerp(event.tickDelta, entity.prevX, entity.getX());
                double y = MathHelper.lerp(event.tickDelta, entity.prevY, entity.getY());
                double z = MathHelper.lerp(event.tickDelta, entity.prevZ, entity.getZ());
                double halfHeight = (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) / 2.0;
                y += halfHeight;

                Color c = new Color(tracerColor.get());
                event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z, x, y, z, c);
            }
        }

        if (!currentLlamas.isEmpty() && !currentLlamas.equals(detectedLlamas)) {
            Set<Integer> newLlamas = new HashSet<>(currentLlamas);
            newLlamas.removeAll(detectedLlamas);
            if (!newLlamas.isEmpty()) {
                detectedLlamas.addAll(newLlamas);
                handleLlamaDetection(newLlamas.size());
            }
        } else if (currentLlamas.isEmpty()) {
            detectedLlamas.clear();
        }
    }

    private void handleLlamaDetection(int llamaCount) {
        String title = "WanderingLamaESP";
        String message = (llamaCount == 1)
            ? "Wandering trader llama detected!"
            : String.format("%d wandering trader llamas detected!", llamaCount);

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

    private void disconnectFromServer(String reason) {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            info("Disconnected from server - %s", reason);
        }
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
