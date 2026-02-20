package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HoleTunnelStairsESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHParams = settings.createGroup("Hole Parameters");
    private final SettingGroup sgTParams = settings.createGroup("Tunnel Parameters");
    private final SettingGroup sgSParams = settings.createGroup("Stairs Parameters");
    private final SettingGroup sgRender = settings.createGroup("Rendering");

    private final Setting<DetectionMode> detectionMode = sgGeneral.add(
        new EnumSetting.Builder<DetectionMode>()
            .name("detection-mode")
            .description("Choose what to detect: holes, tunnels, stairs, or all.")
            .defaultValue(DetectionMode.ALL)
            .build()
    );

    private final Setting<Integer> maxChunks = sgGeneral.add(
        new IntSetting.Builder()
            .name("chunks-per-tick")
            .description("Amount of chunks to process per tick.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 100)
            .build()
    );

    private final Setting<Boolean> airBlocks = sgGeneral.add(
        new BoolSetting.Builder()
            .name("only-air-passable")
            .description("Only marks tunnels/holes if their blocks are air (instead of passable).")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(
        new IntSetting.Builder()
            .name("min-y-offset")
            .description("Scan blocks above or at this many blocks from min build limit.")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 319)
            .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-y-offset")
            .description("Scan blocks below or at this many blocks from max build limit.")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 319)
            .build()
    );

    private final Setting<Integer> minHoleDepth = sgHParams.add(
        new IntSetting.Builder()
            .name("min-hole-depth")
            .description("Minimum depth for a hole to be detected.")
            .defaultValue(4)
            .min(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<Integer> minTunnelLength = sgTParams.add(
        new IntSetting.Builder()
            .name("min-tunnel-length")
            .description("Minimum length for a tunnel to be detected.")
            .defaultValue(3)
            .min(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<Integer> minTunnelHeight = sgTParams.add(
        new IntSetting.Builder()
            .name("min-tunnel-height")
            .description("Minimum height of tunnels to be detected.")
            .defaultValue(2)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> maxTunnelHeight = sgTParams.add(
        new IntSetting.Builder()
            .name("max-tunnel-height")
            .description("Maximum height of tunnels to be detected.")
            .defaultValue(3)
            .min(2)
            .sliderMax(10)
            .build()
    );

    private final Setting<Boolean> diagonals = sgTParams.add(
        new BoolSetting.Builder()
            .name("detect-diagonals")
            .description("Detect diagonal tunnels.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> minDiagonalLength = sgTParams.add(
        new IntSetting.Builder()
            .name("min-diagonal-length")
            .description("Minimum length for diagonal tunnels to be detected.")
            .defaultValue(3)
            .min(1)
            .sliderMax(20)
            .visible(diagonals::get)
            .build()
    );

    private final Setting<Integer> minDiagonalWidth = sgTParams.add(
        new IntSetting.Builder()
            .name("min-diagonal-width")
            .description("Minimum width for diagonal tunnels to be detected.")
            .defaultValue(2)
            .min(2)
            .sliderMax(10)
            .visible(diagonals::get)
            .build()
    );

    private final Setting<Integer> maxDiagonalWidth = sgTParams.add(
        new IntSetting.Builder()
            .name("max-diagonal-width")
            .description("Maximum width for diagonal tunnels to be detected.")
            .defaultValue(4)
            .min(2)
            .sliderMax(10)
            .visible(diagonals::get)
            .build()
    );

    private final Setting<Integer> minStaircaseLength = sgSParams.add(
        new IntSetting.Builder()
            .name("min-staircase-length")
            .description("Minimum length for a staircase to be detected.")
            .defaultValue(3)
            .min(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<Integer> minStaircaseHeight = sgSParams.add(
        new IntSetting.Builder()
            .name("min-staircase-height")
            .description("Minimum height of staircase headroom.")
            .defaultValue(3)
            .min(2)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> maxStaircaseHeight = sgSParams.add(
        new IntSetting.Builder()
            .name("max-staircase-height")
            .description("Maximum height of staircase headroom.")
            .defaultValue(5)
            .min(2)
            .sliderMax(10)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> holeLineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("hole-1x1-line")
            .description("Line color for 1x1 holes.")
            .defaultValue(new SettingColor(255, 0, 0, 95))
            .build()
    );

    private final Setting<SettingColor> holeSideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("hole-1x1-side")
            .description("Side color for 1x1 holes.")
            .defaultValue(new SettingColor(255, 0, 0, 30))
            .build()
    );

    private final Setting<SettingColor> hole3x1LineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("hole-3x1-line")
            .description("Line color for 3x1 holes.")
            .defaultValue(new SettingColor(255, 165, 0, 95))
            .build()
    );

    private final Setting<SettingColor> hole3x1SideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("hole-3x1-side")
            .description("Side color for 3x1 holes.")
            .defaultValue(new SettingColor(255, 165, 0, 30))
            .build()
    );

    private final Setting<SettingColor> tunnelLineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("tunnel-line")
            .description("Line color for tunnels.")
            .defaultValue(new SettingColor(0, 0, 255, 95))
            .build()
    );

    private final Setting<SettingColor> tunnelSideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("tunnel-side")
            .description("Side color for tunnels.")
            .defaultValue(new SettingColor(0, 0, 255, 30))
            .build()
    );

    private final Setting<SettingColor> staircaseLineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("stairs-line")
            .description("Line color for staircases.")
            .defaultValue(new SettingColor(255, 0, 255, 95))
            .build()
    );

    private final Setting<SettingColor> staircaseSideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("stairs-side")
            .description("Side color for staircases.")
            .defaultValue(new SettingColor(255, 0, 255, 30))
            .build()
    );

    private static final Direction[] DIRECTIONS = new Direction[] {
        Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH
    };

    private final ArrayDeque<Long> chunkQueue = new ArrayDeque<>();
    private final Set<Long> queuedKeys = ConcurrentHashMap.newKeySet();
    private final Set<Long> loadedKeys = ConcurrentHashMap.newKeySet();

    private final Set<Box> holes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Box> tunnels = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Box> staircases = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Box> holes3x1 = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Long, Set<Box>> holesByChunk = new ConcurrentHashMap<>();
    private final Map<Long, Set<Box>> tunnelsByChunk = new ConcurrentHashMap<>();
    private final Map<Long, Set<Box>> staircasesByChunk = new ConcurrentHashMap<>();
    private final Map<Long, Set<Box>> holes3x1ByChunk = new ConcurrentHashMap<>();

    public HoleTunnelStairsESP() {
        super(AnubisAddon.ESP, "hole-tunnel-stairs-esp", "Finds and highlights holes, tunnels and staircases.");
    }

    @Override
    public void onActivate() {
        clearState();
        if (mc.world == null) return;

        for (Chunk c : Utils.chunks(true)) {
            ChunkPos cp = c.getPos();
            queueChunkForScan(cp.x, cp.z);
        }
    }

    @Override
    public void onDeactivate() {
        clearState();
    }

    public void onChunkLoaded(int chunkX, int chunkZ) {
        queueChunkForScan(chunkX, chunkZ);
    }

    public void onChunkUnloaded(ChunkPos pos) {
        if (pos == null) return;
        long key = ChunkPos.toLong(pos.x, pos.z);
        loadedKeys.remove(key);
        queuedKeys.remove(key);
        synchronized (chunkQueue) {
            chunkQueue.remove(key);
        }
        removeAllBoxesForChunk(key);
    }

    private void clearState() {
        chunkQueue.clear();
        queuedKeys.clear();
        loadedKeys.clear();
        holes.clear();
        tunnels.clear();
        staircases.clear();
        holes3x1.clear();
        holesByChunk.clear();
        tunnelsByChunk.clear();
        staircasesByChunk.clear();
        holes3x1ByChunk.clear();
    }

    private void queueChunkForScan(int chunkX, int chunkZ) {
        long key = ChunkPos.toLong(chunkX, chunkZ);
        loadedKeys.add(key);
        removeAllBoxesForChunk(key);
        if (queuedKeys.add(key)) {
            synchronized (chunkQueue) {
                chunkQueue.add(key);
            }
        }
    }

    public Set<Box> getHoles() {
        return new HashSet<>(holes);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;
        processChunkQueue();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        switch (detectionMode.get()) {
            case ALL -> {
                renderHoles(event.renderer);
                renderTunnels(event.renderer);
                renderStaircases(event.renderer);
                render3x1Holes(event.renderer);
            }
            case HOLES_AND_TUNNELS -> {
                renderHoles(event.renderer);
                renderTunnels(event.renderer);
                render3x1Holes(event.renderer);
            }
            case HOLES_AND_STAIRCASES -> {
                renderHoles(event.renderer);
                renderStaircases(event.renderer);
                render3x1Holes(event.renderer);
            }
            case TUNNELS_AND_STAIRCASES -> {
                renderTunnels(event.renderer);
                renderStaircases(event.renderer);
            }
            case HOLES -> {
                renderHoles(event.renderer);
                render3x1Holes(event.renderer);
            }
            case TUNNELS -> renderTunnels(event.renderer);
            case STAIRCASES -> renderStaircases(event.renderer);
            case HOLES_3X1_AND_TUNNELS -> {
                renderHoles(event.renderer);
                render3x1Holes(event.renderer);
                renderTunnels(event.renderer);
            }
        }
    }

    private void renderHoles(Renderer3D r) {
        Color side = new Color(holeSideColor.get());
        Color line = new Color(holeLineColor.get());
        for (Box b : holes) r.box(b, side, line, shapeMode.get(), 0);
    }

    private void render3x1Holes(Renderer3D r) {
        Color side = new Color(hole3x1SideColor.get());
        Color line = new Color(hole3x1LineColor.get());
        for (Box b : holes3x1) r.box(b, side, line, shapeMode.get(), 0);
    }

    private void renderTunnels(Renderer3D r) {
        Color side = new Color(tunnelSideColor.get());
        Color line = new Color(tunnelLineColor.get());
        for (Box b : tunnels) r.box(b, side, line, shapeMode.get(), 0);
    }

    private void renderStaircases(Renderer3D r) {
        Color side = new Color(staircaseSideColor.get());
        Color line = new Color(staircaseLineColor.get());
        for (Box b : staircases) r.box(b, side, line, shapeMode.get(), 0);
    }

    private void processChunkQueue() {
        int limit = maxChunks.get();
        int processed = 0;

        while (processed < limit) {
            Long key;
            synchronized (chunkQueue) {
                key = chunkQueue.poll();
            }
            if (key == null) break;

            queuedKeys.remove(key);
            long chunkKey = key;
            MeteorExecutor.execute(() -> searchChunk(chunkKey));
            processed++;
        }
    }

    private void searchChunk(long chunkKey) {
        if (mc.world == null) return;
        if (!loadedKeys.contains(chunkKey)) return;

        int chunkX = ChunkPos.getPackedX(chunkKey);
        int chunkZ = ChunkPos.getPackedZ(chunkKey);
        WorldChunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) return;

        ChunkSection[] sections = chunk.getSectionArray();

        int yMin = mc.world.getBottomY() + minY.get();
        int yMax = (mc.world.getTopYInclusive() + 1) - maxY.get();

        ChunkPos cp = chunk.getPos();
        int startX = cp.getStartX();
        int startZ = cp.getStartZ();

        int baseY = mc.world.getBottomY();

        int sectionBaseY = baseY;
        for (ChunkSection section : sections) {
            if (section != null && !section.isEmpty()) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            int worldY = sectionBaseY + y;
                            if (worldY <= yMin || worldY >= yMax) continue;

                            BlockPos pos = new BlockPos(startX + x, worldY, startZ + z);
                            if (!isPassableBlock(pos)) continue;

                            switch (detectionMode.get()) {
                                case ALL -> {
                                    checkHole(pos, chunkKey);
                                    check3x1Hole(pos, chunkKey);
                                    checkTunnel(pos, chunkKey);
                                    if (diagonals.get()) checkDiagonalTunnel(pos, chunkKey);
                                    checkStaircase(pos, chunkKey);
                                }
                                case HOLES_AND_TUNNELS -> {
                                    checkHole(pos, chunkKey);
                                    check3x1Hole(pos, chunkKey);
                                    checkTunnel(pos, chunkKey);
                                    if (diagonals.get()) checkDiagonalTunnel(pos, chunkKey);
                                }
                                case HOLES_AND_STAIRCASES -> {
                                    checkHole(pos, chunkKey);
                                    check3x1Hole(pos, chunkKey);
                                    checkStaircase(pos, chunkKey);
                                }
                                case TUNNELS_AND_STAIRCASES -> {
                                    checkTunnel(pos, chunkKey);
                                    if (diagonals.get()) checkDiagonalTunnel(pos, chunkKey);
                                    checkStaircase(pos, chunkKey);
                                }
                                case HOLES -> {
                                    checkHole(pos, chunkKey);
                                    check3x1Hole(pos, chunkKey);
                                }
                                case TUNNELS -> {
                                    checkTunnel(pos, chunkKey);
                                    if (diagonals.get()) checkDiagonalTunnel(pos, chunkKey);
                                }
                                case STAIRCASES -> checkStaircase(pos, chunkKey);
                                case HOLES_3X1_AND_TUNNELS -> {
                                    checkHole(pos, chunkKey);
                                    check3x1Hole(pos, chunkKey);
                                    checkTunnel(pos, chunkKey);
                                    if (diagonals.get()) checkDiagonalTunnel(pos, chunkKey);
                                }
                            }
                        }
                    }
                }
            }
            sectionBaseY += 16;
        }
    }

    private void checkHole(BlockPos pos, long chunkKey) {
        if (!isValidHoleSection(pos)) return;

        BlockPos.Mutable cur = pos.mutableCopy();
        while (isValidHoleSection(cur)) cur.move(Direction.UP);

        int depth = cur.getY() - pos.getY();
        if (depth < minHoleDepth.get()) return;

        Box hole = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, cur.getY(), pos.getZ() + 1);

        if (holes.contains(hole)) return;
        if (holes.stream().anyMatch(existing -> existing.intersects(hole))) return;

        holes.add(hole);
        indexBox(holesByChunk, chunkKey, hole);
    }

    private void check3x1Hole(BlockPos pos, long chunkKey) {
        if (isValid3x1HoleSectionX(pos)) {
            BlockPos.Mutable cur = pos.mutableCopy();
            while (isValid3x1HoleSectionX(cur)) cur.move(Direction.UP);

            int depth = cur.getY() - pos.getY();
            if (depth >= minHoleDepth.get()) {
                Box hole = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 3, cur.getY(), pos.getZ() + 1);
                if (!holes3x1.contains(hole) && holes3x1.stream().noneMatch(e -> e.intersects(hole))) {
                    holes3x1.add(hole);
                    indexBox(holes3x1ByChunk, chunkKey, hole);
                }
            }
        }

        if (isValid3x1HoleSectionZ(pos)) {
            BlockPos.Mutable cur = pos.mutableCopy();
            while (isValid3x1HoleSectionZ(cur)) cur.move(Direction.UP);

            int depth = cur.getY() - pos.getY();
            if (depth >= minHoleDepth.get()) {
                Box hole = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, cur.getY(), pos.getZ() + 3);
                if (!holes3x1.contains(hole) && holes3x1.stream().noneMatch(e -> e.intersects(hole))) {
                    holes3x1.add(hole);
                    indexBox(holes3x1ByChunk, chunkKey, hole);
                }
            }
        }
    }

    private boolean isValidHoleSection(BlockPos pos) {
        return isPassableBlock(pos)
            && !isPassableBlock(pos.north())
            && !isPassableBlock(pos.south())
            && !isPassableBlock(pos.east())
            && !isPassableBlock(pos.west());
    }

    private boolean isValid3x1HoleSectionX(BlockPos pos) {
        return isPassableBlock(pos)
            && isPassableBlock(pos.east())
            && isPassableBlock(pos.east(2))
            && !isPassableBlock(pos.west())
            && !isPassableBlock(pos.east(3))
            && !isPassableBlock(pos.north())
            && !isPassableBlock(pos.south())
            && !isPassableBlock(pos.east().north())
            && !isPassableBlock(pos.east().south())
            && !isPassableBlock(pos.east(2).north())
            && !isPassableBlock(pos.east(2).south());
    }

    private boolean isValid3x1HoleSectionZ(BlockPos pos) {
        return isPassableBlock(pos)
            && isPassableBlock(pos.south())
            && isPassableBlock(pos.south(2))
            && !isPassableBlock(pos.north())
            && !isPassableBlock(pos.south(3))
            && !isPassableBlock(pos.east())
            && !isPassableBlock(pos.west())
            && !isPassableBlock(pos.south().east())
            && !isPassableBlock(pos.south().west())
            && !isPassableBlock(pos.south(2).east())
            && !isPassableBlock(pos.south(2).west());
    }

    private void checkTunnel(BlockPos pos, long chunkKey) {
        for (Direction dir : DIRECTIONS) {
            BlockPos.Mutable cur = pos.mutableCopy();

            int steps = 0;
            BlockPos start = null;
            BlockPos end = null;
            int maxHeightFound = 0;

            if (isTunnelSection(cur, dir)) start = cur.toImmutable();

            while (isTunnelSection(cur, dir)) {
                maxHeightFound = Math.max(maxHeightFound, getTunnelHeight(cur));
                end = cur.toImmutable();
                cur.move(dir);
                steps++;
            }

            if (start == null || end == null) continue;
            if (steps < minTunnelLength.get()) continue;
            if (maxHeightFound < minTunnelHeight.get() || maxHeightFound > maxTunnelHeight.get()) continue;

            int minX = Math.min(start.getX(), end.getX());
            int minZ = Math.min(start.getZ(), end.getZ());
            int maxX = Math.max(start.getX(), end.getX()) + 1;
            int maxZ = Math.max(start.getZ(), end.getZ()) + 1;

            Box tunnel = new Box(minX, start.getY(), minZ, maxX, start.getY() + maxHeightFound, maxZ);

            if (tunnels.contains(tunnel)) continue;
            if (tunnels.stream().anyMatch(e -> e.intersects(tunnel))) continue;

            tunnels.add(tunnel);
            indexBox(tunnelsByChunk, chunkKey, tunnel);
        }
    }

    private boolean isTunnelSection(BlockPos pos, Direction dir) {
        int height = getTunnelHeight(pos);
        if (height < minTunnelHeight.get() || height > maxTunnelHeight.get()) return false;

        if (isPassableBlock(pos.down()) || isPassableBlock(pos.up(height))) return false;

        Direction[] perp = (dir.getAxis() == Direction.Axis.X)
            ? new Direction[] {Direction.NORTH, Direction.SOUTH}
            : new Direction[] {Direction.EAST, Direction.WEST};

        for (Direction p : perp) {
            for (int i = 0; i < height; i++) {
                if (isPassableBlock(pos.up(i).offset(p))) return false;
            }
        }

        return true;
    }

    private void checkDiagonalTunnel(BlockPos pos, long chunkKey) {
        for (Direction dir : DIRECTIONS) {
            for (int step = minDiagonalWidth.get() - 1; step < maxDiagonalWidth.get(); step++) {
                BlockPos.Mutable cur = pos.mutableCopy();
                int steps = 0;

                ArrayList<Box> potential = new ArrayList<>();
                Direction checking = dir;
                boolean turnRight = true;

                while (isDiagonalTunnelSection(cur, checking)) {
                    int height = getTunnelHeight(cur);

                    Box b = new Box(cur.getX(), cur.getY(), cur.getZ(), cur.getX() + 1, cur.getY() + height, cur.getZ() + 1);
                    if (potential.stream().noneMatch(e -> e.intersects(b)) && !potential.contains(b)) potential.add(b);

                    if (turnRight) {
                        checking = checking.rotateYClockwise();
                        cur.move(checking.rotateYClockwise(), step);
                        turnRight = false;
                    } else {
                        checking = checking.rotateYCounterclockwise();
                        cur.move(checking.rotateYCounterclockwise(), step);
                        turnRight = true;
                    }

                    steps++;
                }

                if (minDiagonalWidth.get() <= 0) continue;
                if (steps / minDiagonalWidth.get() < minDiagonalLength.get()) continue;

                for (Box b : potential) {
                    if (!tunnels.contains(b) && tunnels.stream().noneMatch(e -> e.intersects(b))) {
                        tunnels.add(b);
                        indexBox(tunnelsByChunk, chunkKey, b);
                    }
                }
            }
        }
    }

    private boolean isDiagonalTunnelSection(BlockPos pos, Direction dir) {
        int height = getTunnelHeight(pos);
        if (height < minTunnelHeight.get() || height > maxTunnelHeight.get()) return false;

        if (isPassableBlock(pos.down()) || isPassableBlock(pos.up(height))) return false;

        for (int i = 0; i < height; i++) {
            if (isPassableBlock(pos.up(i).offset(dir))) return false;
        }

        return true;
    }

    private int getTunnelHeight(BlockPos pos) {
        int h = 0;
        while (h < maxTunnelHeight.get() && isPassableBlock(pos.up(h))) h++;
        return h;
    }

    private void checkStaircase(BlockPos pos, long chunkKey) {
        for (Direction dir : DIRECTIONS) {
            BlockPos.Mutable cur = pos.mutableCopy();
            int steps = 0;

            ArrayList<Box> potential = new ArrayList<>();

            while (isStaircaseSection(cur, dir)) {
                int height = getStaircaseHeight(cur);

                Box b = new Box(cur.getX(), cur.getY(), cur.getZ(), cur.getX() + 1, cur.getY() + height, cur.getZ() + 1);
                if (potential.stream().noneMatch(e -> e.intersects(b)) && !potential.contains(b)) potential.add(b);

                cur.move(dir);
                cur.move(Direction.UP);
                steps++;
            }

            if (steps < minStaircaseLength.get()) continue;

            for (Box b : potential) {
                if (!staircases.contains(b) && staircases.stream().noneMatch(e -> e.intersects(b))) {
                    staircases.add(b);
                    indexBox(staircasesByChunk, chunkKey, b);
                }
            }
        }
    }

    private void indexBox(Map<Long, Set<Box>> byChunk, long key, Box box) {
        byChunk.computeIfAbsent(key, ignored -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(box);
    }

    private void removeAllBoxesForChunk(long key) {
        removeIndexedSet(holesByChunk, holes, key);
        removeIndexedSet(holes3x1ByChunk, holes3x1, key);
        removeIndexedSet(tunnelsByChunk, tunnels, key);
        removeIndexedSet(staircasesByChunk, staircases, key);
    }

    private void removeIndexedSet(Map<Long, Set<Box>> byChunk, Set<Box> global, long key) {
        Set<Box> removed = byChunk.remove(key);
        if (removed != null && !removed.isEmpty()) global.removeAll(removed);
    }

    private int getStaircaseHeight(BlockPos pos) {
        int h = 0;
        while (h < maxStaircaseHeight.get() && isPassableBlock(pos.up(h))) h++;
        return h;
    }

    private boolean isStaircaseSection(BlockPos pos, Direction dir) {
        int height = getStaircaseHeight(pos);
        if (height < minStaircaseHeight.get() || height > maxStaircaseHeight.get()) return false;

        if (isPassableBlock(pos.down()) || isPassableBlock(pos.up(height))) return false;

        Direction[] perp = (dir.getAxis() == Direction.Axis.X)
            ? new Direction[] {Direction.NORTH, Direction.SOUTH}
            : new Direction[] {Direction.EAST, Direction.WEST};

        for (Direction p : perp) {
            for (int i = 0; i < height; i++) {
                if (isPassableBlock(pos.up(i).offset(p))) return false;
            }
        }

        return true;
    }

    private boolean isPassableBlock(BlockPos pos) {
        if (mc.world == null) return false;

        var state = mc.world.getBlockState(pos);

        if (airBlocks.get()) return state.isAir();

        VoxelShape shape = state.getCollisionShape(mc.world, pos);
        return shape.isEmpty() || !VoxelShapes.fullCube().equals(shape);
    }

    public enum DetectionMode {
        ALL,
        HOLES_AND_TUNNELS,
        HOLES_AND_STAIRCASES,
        TUNNELS_AND_STAIRCASES,
        HOLES,
        TUNNELS,
        STAIRCASES,
        HOLES_3X1_AND_TUNNELS
    }

}
