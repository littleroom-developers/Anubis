package com.pedro.anubis.modules.main;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class AnubisFreecam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Camera movement speed.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(5.0)
        .build()
    );

    public final Vector3d pos = new Vector3d();
    public final Vector3d prevPos = new Vector3d();

    public float yaw;
    public float pitch;
    public float prevYaw;
    public float prevPitch;

    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;
    private boolean up;
    private boolean down;

    public AnubisFreecam() {
        super(AnubisAddon.MAIN, "AnubisFreecam", "Smooth Flat-Movement Freecam.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        Vec3d eyePos = mc.gameRenderer.getCamera().getPos();
        pos.set(eyePos.x, eyePos.y, eyePos.z);
        prevPos.set((Vector3dc) pos);

        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        prevYaw = yaw;
        prevPitch = pitch;

        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);

        mc.player.setSprinting(false);
        unpress();

        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    private void unpress() {
        down = false;
        up = false;
        right = false;
        left = false;
        backward = false;
        forward = false;
    }

    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        event.cancel();
    }

    public void changeLookDirection(double dx, double dy) {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double multiplier = s * s * s * 8.0;

        yaw += (float) (dx * multiplier * 0.15);
        pitch += (float) (dy * multiplier * 0.15);
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        prevPos.set((Vector3dc) pos);
        prevYaw = yaw;
        prevPitch = pitch;

        Vec3d fwd = Vec3d.fromPolar(0.0f, yaw);
        Vec3d side = Vec3d.fromPolar(0.0f, yaw + 90.0f);

        double s = speed.get() * (mc.options.sprintKey.isPressed() ? 2.0 : 1.0) * 0.2;
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        if (forward) {
            x += fwd.x * s;
            z += fwd.z * s;
        }

        if (backward) {
            x -= fwd.x * s;
            z -= fwd.z * s;
        }

        if (right) {
            x += side.x * s;
            z += side.z * s;
        }

        if (left) {
            x -= side.x * s;
            z -= side.z * s;
        }

        if (up) y += s;
        if (down) y -= s;

        pos.add(x, y, z);
    }

    public double getX(float t) {
        return MathHelper.lerp(t, prevPos.x, pos.x);
    }

    public double getY(float t) {
        return MathHelper.lerp(t, prevPos.y, pos.y);
    }

    public double getZ(float t) {
        return MathHelper.lerp(t, prevPos.z, pos.z);
    }

    public float getYaw(float t) {
        return MathHelper.lerp(t, prevYaw, yaw);
    }

    public float getPitch(float t) {
        return MathHelper.lerp(t, prevPitch, pitch);
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (mc.currentScreen != null) return;

        boolean isPressed = !event.action.toString().contains("Release");

        if (mc.options.forwardKey.matchesKey(event.key, 0)) {
            forward = isPressed;
        } else if (mc.options.backKey.matchesKey(event.key, 0)) {
            backward = isPressed;
        } else if (mc.options.leftKey.matchesKey(event.key, 0)) {
            left = isPressed;
        } else if (mc.options.rightKey.matchesKey(event.key, 0)) {
            right = isPressed;
        } else if (mc.options.jumpKey.matchesKey(event.key, 0)) {
            up = isPressed;
        } else if (mc.options.sneakKey.matchesKey(event.key, 0)) {
            down = isPressed;
        } else {
            return;
        }

        event.cancel();
    }
}
