package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import com.pedro.anubis.modules.esp.chunkfinder.ChunkFinderRuntime;
import com.pedro.anubis.modules.esp.chunkfinder.VineChunkData;
import com.pedro.anubis.modules.esp.chunkfinder.VineChunkScanner;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

public class VineESP extends Module {
    private static final int THREAD_POOL_SIZE = 2;
    private static final long CLEANUP_INTERVAL_MS = 30_000L;

    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> vineMinLength = sgGeneral.add(new IntSetting.Builder()
        .name("vine-min-length")
        .description("Minimum vine length to count as grounded vine signal.")
        .defaultValue(17)
        .range(1, 128)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Integer> vineChunkThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("vine-chunk-threshold")
        .description("Minimum grounded long vines required to flag a chunk.")
        .defaultValue(5)
        .range(1, 64)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> vineMinY = sgGeneral.add(new IntSetting.Builder()
        .name("vine-min-y")
        .description("Minimum Y to scan for vines.")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> vineMaxY = sgGeneral.add(new IntSetting.Builder()
        .name("vine-max-y")
        .description("Maximum Y to scan for vines.")
        .defaultValue(320)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> maxChunksRender = sgRender.add(new IntSetting.Builder()
        .name("max-chunks-render")
        .description("Maximum highlighted chunks to render.")
        .defaultValue(80)
        .range(1, 500)
        .sliderRange(1, 150)
        .build()
    );

    private final Setting<SettingColor> vineColor = sgRender.add(new ColorSetting.Builder()
        .name("vine-color")
        .description("Color for vine chunks.")
        .defaultValue(new SettingColor(0, 110, 0, 60))
        .build()
    );

    private ChunkFinderRuntime<VineChunkData> runtime;

    public VineESP() {
        super(AnubisAddon.ESP, "vine-esp", "Highlights chunks with grounded long vines.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        VineChunkScanner scanner = new VineChunkScanner(mc.world, vineMinLength::get, vineMinY::get, vineMaxY::get);
        runtime = new ChunkFinderRuntime<>(mc, scanner, data -> data.hasSignal(vineChunkThreshold.get()), (cp, data) -> {}, THREAD_POOL_SIZE);
        runtime.activate();
        runtime.startInitialScan();
    }

    @Override
    public void onDeactivate() {
        if (runtime != null) {
            runtime.deactivate();
            runtime = null;
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (runtime != null) runtime.onChunkLoad(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (runtime != null) runtime.onBlockUpdate(event.pos);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (runtime == null || mc.player == null) return;
        int cleanupBuffer = mc.options.getViewDistance().getValue() + 5;
        runtime.tickCleanup(System.currentTimeMillis(), CLEANUP_INTERVAL_MS, mc.player.getChunkPos(), cleanupBuffer);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (runtime == null || mc.player == null) return;

        Color color = new Color(vineColor.get());
        int rendered = 0;
        for (ChunkPos cp : runtime.flaggedChunks()) {
            if (rendered++ >= maxChunksRender.get()) break;

            double x1 = cp.getStartX();
            double z1 = cp.getStartZ();
            Box box = new Box(x1, 64.0, z1, x1 + 16.0, 64.5, z1 + 16.0);
            event.renderer.box(box, color, color, ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() {
        return runtime == null ? "0" : String.valueOf(runtime.flaggedCount());
    }
}
