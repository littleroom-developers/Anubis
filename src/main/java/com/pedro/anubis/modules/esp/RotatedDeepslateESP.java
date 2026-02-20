package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RotatedDeepslateESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgFiltering = settings.createGroup("Block Types");
    private final SettingGroup sgRange = settings.createGroup("Range");

    private final Setting<SettingColor> deepslateColor = sgGeneral.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("The color of the ESP box and tracers.")
        .defaultValue(new SettingColor(255, 0, 255, 100))
        .build()
    );

    private final Setting<ShapeMode> deepslateShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> ignoreExposed = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-exposed")
        .description("Only show blocks that are completely buried (not touching air/water).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw lines from your crosshair/camera to the blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> includeRegular = sgFiltering.add(new BoolSetting.Builder()
        .name("regular-deepslate")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeBricks = sgFiltering.add(new BoolSetting.Builder()
        .name("deepslate-bricks")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeTiles = sgFiltering.add(new BoolSetting.Builder()
        .name("deepslate-tiles")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minY = sgRange.add(new IntSetting.Builder()
        .name("min-y")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> maxY = sgRange.add(new IntSetting.Builder()
        .name("max-y")
        .defaultValue(128)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> renderDistance = sgRange.add(new IntSetting.Builder()
        .name("render-distance")
        .description("Max render distance in blocks.")
        .defaultValue(256)
        .range(32, 1024)
        .sliderRange(32, 1024)
        .build()
    );

    private final Set<BlockPos> rotatedPositions = ConcurrentHashMap.newKeySet();
    private ExecutorService threadPool;

    public RotatedDeepslateESP() {
        super(AnubisAddon.ESP, "rotated-deepslate-esp", "Highlights deepslate blocks that have been rotated by players (axis is not Y).");
    }

    @Override
    public void onActivate() {
        rotatedPositions.clear();

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        threadPool = Executors.newFixedThreadPool(threads);

        for (Chunk chunk : Utils.chunks()) {
            if (chunk == null) continue;
            threadPool.submit(() -> scanChunk(chunk));
        }
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null) threadPool.shutdownNow();
        threadPool = null;
        rotatedPositions.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (!isActive()) return;
        if (threadPool != null && !threadPool.isShutdown()) {
            Chunk c = event.chunk();
            if (c != null) threadPool.submit(() -> scanChunk(c));
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!isActive() || mc.world == null) return;

        BlockPos pos = event.pos.toImmutable();
        BlockState state = event.newState;

        if (isRotated(state, pos, mc.world)) rotatedPositions.add(pos);
        else rotatedPositions.remove(pos);
    }

    private void scanChunk(Chunk chunk) {
        if (chunk == null || mc.world == null) return;

        ChunkPos cpos = chunk.getPos();
        int startX = cpos.getStartX();
        int startZ = cpos.getStartZ();

        int bottomY = chunk.getBottomY();
        int topY = bottomY + chunk.getHeight() - 1;

        int yMin = Math.max(minY.get(), bottomY);
        int yMax = Math.min(maxY.get(), topY);

        World world = mc.world;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                    BlockState state = chunk.getBlockState(pos);

                    if (isRotated(state, pos, world)) rotatedPositions.add(pos.toImmutable());
                }
            }
        }
    }

    private boolean isRotated(BlockState state, BlockPos pos, World world) {
        if (state == null || state.isAir()) return false;

        int y = pos.getY();
        if (y < minY.get() || y > maxY.get()) return false;

        boolean validBlock =
            (includeRegular.get() && state.isOf(Blocks.DEEPSLATE))
                || (includeBricks.get() && state.isOf(Blocks.DEEPSLATE_BRICKS))
                || (includeTiles.get() && state.isOf(Blocks.DEEPSLATE_TILES));

        if (!validBlock) return false;
        if (!state.contains(Properties.AXIS)) return false;

        Direction.Axis axis = state.get(Properties.AXIS);
        if (axis == Direction.Axis.Y) return false;

        if (ignoreExposed.get() && world != null) {
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.offset(dir);
                if (world.isOutOfHeightLimit(n)) return false;
                if (world.getBlockState(n).isAir()) return false;
                if (!world.getFluidState(n).isEmpty()) return false;
            }
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getPos();
        Color side = new Color(deepslateColor.get());
        Color line = new Color(deepslateColor.get());
        line.a = 255;

        int rd = renderDistance.get();
        double rdSq = (double) rd * rd;
        Vec3d start = RenderUtils.center != null ? RenderUtils.center : mc.gameRenderer.getCamera().getPos();

        for (BlockPos pos : rotatedPositions) {
            double dx = (pos.getX() + 0.5) - playerPos.x;
            double dy = (pos.getY() + 0.5) - playerPos.y;
            double dz = (pos.getZ() + 0.5) - playerPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > rdSq) continue;

            event.renderer.box(pos, side, line, deepslateShapeMode.get(), 0);

            if (tracers.get()) {
                Vec3d center = Vec3d.ofCenter(pos);
                event.renderer.line(start.x, start.y, start.z, center.x, center.y, center.z, line);
            }
        }
    }
}
