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
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class WanderingESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draw tracer lines to wandering traders.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the tracer lines.")
        .defaultValue(new SettingColor(0, 255, 0, 127))
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Automatically disconnect when wandering traders are detected.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when wandering traders are detected.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-when-found")
        .description("Automatically toggles the module when a wandering trader is detected.")
        .defaultValue(false)
        .build()
    );

    private final Set<Integer> detectedTraders = new HashSet<>();

    public WanderingESP() {
        super(AnubisAddon.ESP, "wandering-esp", "Detects wandering traders in the world.");
    }

    @Override
    public void onActivate() {
        detectedTraders.clear();
    }

    @Override
    public void onDeactivate() {
        detectedTraders.clear();
    }

    @Override
    public String getInfoString() {
        return detectedTraders.isEmpty() ? null : String.valueOf(detectedTraders.size());
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d tracerStart = RenderUtils.center != null ? RenderUtils.center : mc.gameRenderer.getCamera().getPos();
        Box searchBox = mc.player.getBoundingBox().expand(4096);
        Set<Integer> currentTraders = new HashSet<>();

        for (WanderingTraderEntity trader : mc.world.getEntitiesByType(
            TypeFilter.instanceOf(WanderingTraderEntity.class),
            searchBox,
            e -> true
        )) {
            currentTraders.add(trader.getId());

            if (!showTracers.get()) continue;

            double x = MathHelper.lerp(event.tickDelta, trader.prevX, trader.getX());
            double y = MathHelper.lerp(event.tickDelta, trader.prevY, trader.getY());
            double z = MathHelper.lerp(event.tickDelta, trader.prevZ, trader.getZ());
            y += trader.getBoundingBox().getLengthY() / 2.0;

            Color c = new Color(tracerColor.get());
            event.renderer.line(tracerStart.x, tracerStart.y, tracerStart.z, x, y, z, c);
        }

        if (!currentTraders.isEmpty() && !currentTraders.equals(detectedTraders)) {
            Set<Integer> newTraders = new HashSet<>(currentTraders);
            newTraders.removeAll(detectedTraders);

            if (!newTraders.isEmpty()) {
                detectedTraders.addAll(newTraders);
                handleTraderDetection(newTraders.size());
            }
        } else if (currentTraders.isEmpty()) {
            detectedTraders.clear();
        }
    }

    private void handleTraderDetection(int traderCount) {
        String title = "WanderingESP";
        String message = (traderCount == 1)
            ? "Wandering trader detected!"
            : String.format("%d wandering traders detected!", traderCount);

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
