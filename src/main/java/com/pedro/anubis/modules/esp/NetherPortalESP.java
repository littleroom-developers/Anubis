package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NetherPortalESP extends Module {
    private static final int MIN_FRAME_OUTER_WIDTH = 4;
    private static final int MIN_FRAME_OUTER_HEIGHT = 5;
    private static final int MAX_FRAME_OUTER_WIDTH = 23;
    private static final int MAX_FRAME_OUTER_HEIGHT = 23;
    private static final int CLEANUP_INTERVAL_TICKS = 100;

    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotify = settings.createGroup("Notify");

    private final Setting<DetectionMode> detectionMode = sgGeneral.add(new EnumSetting.Builder<DetectionMode>()
        .name("detection-mode")
        .description("Detect active portal blocks, full obsidian frames, or both.")
        .defaultValue(DetectionMode.Both)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("How many queued chunks to scan per tick.")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> maxPortalsRender = sgRender.add(new IntSetting.Builder()
        .name("max-portals-render")
        .description("Maximum detected structures rendered at once.")
        .defaultValue(120)
        .range(1, 500)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<SettingColor> portalColor = sgRender.add(new ColorSetting.Builder()
        .name("portal-color")
        .description("Color for Nether portal ESP.")
        .defaultValue(new SettingColor(170, 0, 255, 90))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the ESP should be rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to detected portals.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toastOnDiscovery = sgNotify.add(new BoolSetting.Builder()
        .name("toast-on-discovery")
        .description("Show a toast when a new portal or frame is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> toastCooldownSeconds = sgNotify.add(new IntSetting.Builder()
        .name("toast-cooldown-seconds")
        .description("Minimum seconds between toasts for the same structure.")
        .defaultValue(30)
        .range(1, 600)
        .sliderRange(5, 120)
        .visible(toastOnDiscovery::get)
        .build()
    );

    private final Map<Long, List<PortalStructure>> structuresByAnchorChunk = new HashMap<>();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunkKeys = new HashSet<>();
    private final Map<String, Long> lastToastTimes = new HashMap<>();

    private DetectionMode lastDetectionMode = DetectionMode.Both;
    private int tickCounter;

    public NetherPortalESP() {
        super(AnubisAddon.ESP, "netherportalESP", "Highlights active Nether portals and fully completed obsidian frames.");
    }

    @Override
    public void onActivate() {
        clearState();
        lastDetectionMode = detectionMode.get();
        queueAllLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        clearState();
    }

    private void clearState() {
        structuresByAnchorChunk.clear();
        scanQueue.clear();
        queuedChunkKeys.clear();
        lastToastTimes.clear();
        tickCounter = 0;
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        ChunkPos cp = event.chunk().getPos();
        queueChunkAndNeighbors(cp.x, cp.z);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!affectsDetection(event.oldState) && !affectsDetection(event.newState)) return;
        ChunkPos cp = new ChunkPos(event.pos);
        queueChunkAndNeighbors(cp.x, cp.z);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        if (lastDetectionMode != detectionMode.get()) {
            lastDetectionMode = detectionMode.get();
            queueAllLoadedChunks();
        }

        processScanQueue();

        tickCounter++;
        if (tickCounter >= CLEANUP_INTERVAL_TICKS) {
            cleanupFarChunks();
            tickCounter = 0;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        int renderLimit = maxPortalsRender.get();
        if (renderLimit <= 0) return;

        DetectionMode mode = detectionMode.get();
        int viewDistance = mc.options.getViewDistance().getValue();
        int chunkRadius = viewDistance + 1;
        double maxDistance = (viewDistance * 16.0 * Math.sqrt(2.0)) + 16.0;
        double maxDistanceSq = maxDistance * maxDistance;

        ChunkPos playerChunk = mc.player.getChunkPos();
        Vec3d playerPos = mc.player.getPos();
        Vec3d tracerStart = RenderUtils.center != null ? RenderUtils.center : mc.gameRenderer.getCamera().getPos();

        List<PortalStructure> frames = new ArrayList<>();
        List<PortalStructure> portals = new ArrayList<>();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                long key = ChunkPos.toLong(playerChunk.x + dx, playerChunk.z + dz);
                List<PortalStructure> list = structuresByAnchorChunk.get(key);
                if (list == null || list.isEmpty()) continue;

                for (PortalStructure structure : list) {
                    if (!matchesMode(structure.type, mode)) continue;
                    if (squaredDistance(playerPos, structure.center) > maxDistanceSq) continue;

                    if (structure.type == StructureType.Frame) frames.add(structure);
                    else portals.add(structure);
                }
            }
        }

        if (mode == DetectionMode.Both && !frames.isEmpty() && !portals.isEmpty()) {
            portals.removeIf(portal -> isEnclosedByAnyFrame(portal.box, frames));
        }

        Color fill = new Color(portalColor.get());
        Color line = new Color(portalColor.get());
        line.a = 255;

        int rendered = 0;
        for (PortalStructure structure : frames) {
            if (rendered++ >= renderLimit) break;
            renderStructure(event, structure, fill, line, tracerStart);
        }

        for (PortalStructure structure : portals) {
            if (rendered++ >= renderLimit) break;
            renderStructure(event, structure, fill, line, tracerStart);
        }
    }

    @Override
    public String getInfoString() {
        DetectionMode mode = detectionMode.get();
        int count = 0;
        for (List<PortalStructure> list : structuresByAnchorChunk.values()) {
            for (PortalStructure structure : list) {
                if (matchesMode(structure.type, mode)) count++;
            }
        }

        return count == 0 ? null : String.valueOf(count);
    }

    private void renderStructure(Render3DEvent event, PortalStructure structure, Color fill, Color line, Vec3d tracerStart) {
        event.renderer.box(structure.box, fill, line, shapeMode.get(), 0);
        if (tracers.get()) {
            event.renderer.line(
                tracerStart.x, tracerStart.y, tracerStart.z,
                structure.center.x, structure.center.y, structure.center.z,
                line
            );
        }
    }

    private void queueAllLoadedChunks() {
        if (mc.world == null) return;

        for (Chunk chunk : Utils.chunks()) {
            if (chunk == null) continue;
            ChunkPos cp = chunk.getPos();
            queueChunk(cp.x, cp.z);
        }
    }

    private void queueChunkAndNeighbors(int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                queueChunk(chunkX + dx, chunkZ + dz);
            }
        }
    }

    private void queueChunk(int chunkX, int chunkZ) {
        long key = ChunkPos.toLong(chunkX, chunkZ);
        if (!queuedChunkKeys.add(key)) return;
        scanQueue.addLast(key);
    }

    private void processScanQueue() {
        int budget = Math.max(1, chunksPerTick.get());
        for (int i = 0; i < budget; i++) {
            Long key = scanQueue.pollFirst();
            if (key == null) break;
            queuedChunkKeys.remove(key);
            scanSingleChunk(key);
        }
    }

    private void scanSingleChunk(long chunkKey) {
        if (mc.world == null) return;

        int chunkX = ChunkPos.getPackedX(chunkKey);
        int chunkZ = ChunkPos.getPackedZ(chunkKey);
        WorldChunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            structuresByAnchorChunk.remove(chunkKey);
            return;
        }

        List<PortalStructure> newlyDetected = detectInChunk(chunk, detectionMode.get());
        List<PortalStructure> previous = structuresByAnchorChunk.put(chunkKey, newlyDetected);
        notifyForNewStructures(previous, newlyDetected);
    }

    private List<PortalStructure> detectInChunk(WorldChunk chunk, DetectionMode mode) {
        List<PortalStructure> detected = new ArrayList<>();

        boolean scanPortals = mode == DetectionMode.PortalOnly || mode == DetectionMode.Both;
        boolean scanFrames = mode == DetectionMode.FrameOnly || mode == DetectionMode.Both;

        if (scanPortals) detectPortalComponents(chunk, detected);
        if (scanFrames) detectFrames(chunk, detected);

        return detected;
    }

    private void detectPortalComponents(WorldChunk chunk, List<PortalStructure> out) {
        if (mc.world == null) return;

        Set<Long> visited = new HashSet<>();
        ChunkPos chunkPos = chunk.getPos();
        long anchorChunkKey = ChunkPos.toLong(chunkPos.x, chunkPos.z);
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int bottomY = chunk.getBottomY();
        int topY = bottomY + chunk.getHeight();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = bottomY; y < topY; y++) {
                    mutable.set(x, y, z);
                    if (!chunk.getBlockState(mutable).isOf(Blocks.NETHER_PORTAL)) continue;

                    long packed = mutable.asLong();
                    if (visited.contains(packed)) continue;

                    PortalBounds bounds = floodPortalComponent(mutable.toImmutable(), visited);
                    if (bounds == null) continue;

                    long boundsChunkKey = ChunkPos.toLong(Math.floorDiv(bounds.minX, 16), Math.floorDiv(bounds.minZ, 16));
                    if (boundsChunkKey != anchorChunkKey) continue;

                    out.add(createPortalStructure(bounds));
                }
            }
        }
    }

    private PortalBounds floodPortalComponent(BlockPos start, Set<Long> visited) {
        if (mc.world == null || !mc.world.getBlockState(start).isOf(Blocks.NETHER_PORTAL)) return null;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        PortalBounds bounds = new PortalBounds();

        while (!queue.isEmpty()) {
            BlockPos pos = queue.pollFirst();
            long packed = pos.asLong();
            if (visited.contains(packed)) continue;
            if (!mc.world.getBlockState(pos).isOf(Blocks.NETHER_PORTAL)) continue;

            visited.add(packed);
            bounds.include(pos);

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.offset(dir);
                long neighborPacked = neighbor.asLong();
                if (visited.contains(neighborPacked)) continue;
                if (mc.world.getBlockState(neighbor).isOf(Blocks.NETHER_PORTAL)) {
                    queue.addLast(neighbor.toImmutable());
                }
            }
        }

        return bounds.empty ? null : bounds;
    }

    private void detectFrames(WorldChunk chunk, List<PortalStructure> out) {
        if (mc.world == null) return;

        Set<String> localFrameKeys = new HashSet<>();
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int bottomY = chunk.getBottomY();
        int topY = bottomY + chunk.getHeight();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = bottomY; y < topY; y++) {
                    mutable.set(x, y, z);
                    if (!chunk.getBlockState(mutable).isOf(Blocks.OBSIDIAN)) continue;

                    BlockPos corner = mutable.toImmutable();

                    PortalStructure eastFrame = detectFrameFromCorner(corner, Direction.EAST);
                    if (eastFrame != null && localFrameKeys.add(eastFrame.key)) out.add(eastFrame);

                    PortalStructure southFrame = detectFrameFromCorner(corner, Direction.SOUTH);
                    if (southFrame != null && localFrameKeys.add(southFrame.key)) out.add(southFrame);
                }
            }
        }
    }

    private PortalStructure detectFrameFromCorner(BlockPos corner, Direction widthDirection) {
        if (mc.world == null || !isObsidian(corner)) return null;

        if (isObsidian(corner.offset(widthDirection.getOpposite()))) return null;
        if (!isObsidian(corner.offset(widthDirection))) return null;
        if (!isObsidian(corner.up())) return null;

        int maxWidth = contiguousObsidianLength(corner, widthDirection, MAX_FRAME_OUTER_WIDTH);
        int maxHeight = contiguousObsidianLength(corner, Direction.UP, MAX_FRAME_OUTER_HEIGHT);
        if (maxWidth < MIN_FRAME_OUTER_WIDTH || maxHeight < MIN_FRAME_OUTER_HEIGHT) return null;

        for (int width = maxWidth; width >= MIN_FRAME_OUTER_WIDTH; width--) {
            for (int height = maxHeight; height >= MIN_FRAME_OUTER_HEIGHT; height--) {
                if (isValidFrame(corner, widthDirection, width, height)) {
                    return createFrameStructure(corner, widthDirection, width, height);
                }
            }
        }

        return null;
    }

    private int contiguousObsidianLength(BlockPos start, Direction direction, int maxLength) {
        int length = 0;
        while (length < maxLength && isObsidian(start.offset(direction, length))) {
            length++;
        }
        return length;
    }

    private boolean isValidFrame(BlockPos corner, Direction widthDirection, int width, int height) {
        if (mc.world == null) return false;

        int topOffset = height - 1;
        int rightOffset = width - 1;

        for (int i = 0; i < width; i++) {
            if (!isObsidian(corner.offset(widthDirection, i))) return false;
            if (!isObsidian(corner.offset(widthDirection, i).up(topOffset))) return false;
        }

        for (int j = 0; j < height; j++) {
            if (!isObsidian(corner.up(j))) return false;
            if (!isObsidian(corner.offset(widthDirection, rightOffset).up(j))) return false;
        }

        for (int i = 1; i < width - 1; i++) {
            for (int j = 1; j < height - 1; j++) {
                BlockState inside = mc.world.getBlockState(corner.offset(widthDirection, i).up(j));
                if (!isValidInteriorBlock(inside)) return false;
            }
        }

        return true;
    }

    private PortalStructure createPortalStructure(PortalBounds bounds) {
        Box box = new Box(
            bounds.minX, bounds.minY, bounds.minZ,
            bounds.maxX + 1.0, bounds.maxY + 1.0, bounds.maxZ + 1.0
        );

        Vec3d center = new Vec3d(
            (bounds.minX + bounds.maxX + 1) / 2.0,
            (bounds.minY + bounds.maxY + 1) / 2.0,
            (bounds.minZ + bounds.maxZ + 1) / 2.0
        );

        String key = String.format(
            "portal:%d,%d,%d:%d,%d,%d",
            bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ
        );

        BlockPos anchor = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
        return new PortalStructure(key, StructureType.Portal, box, center, anchor);
    }

    private PortalStructure createFrameStructure(BlockPos corner, Direction widthDirection, int width, int height) {
        int minX = corner.getX();
        int minY = corner.getY();
        int minZ = corner.getZ();
        int maxX = minX;
        int maxZ = minZ;

        if (widthDirection.getAxis() == Direction.Axis.X) maxX = minX + width - 1;
        else maxZ = minZ + width - 1;

        int maxY = minY + height - 1;

        Box box = new Box(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        Vec3d center = new Vec3d(
            (minX + maxX + 1) / 2.0,
            (minY + maxY + 1) / 2.0,
            (minZ + maxZ + 1) / 2.0
        );

        String axis = widthDirection.getAxis() == Direction.Axis.X ? "x" : "z";
        String key = String.format("frame:%s:%d,%d,%d:%d,%d,%d", axis, minX, minY, minZ, maxX, maxY, maxZ);

        return new PortalStructure(key, StructureType.Frame, box, center, corner);
    }

    private void notifyForNewStructures(List<PortalStructure> previous, List<PortalStructure> current) {
        if (!toastOnDiscovery.get() || mc.world == null) return;

        Set<String> oldKeys = new HashSet<>();
        if (previous != null) {
            for (PortalStructure structure : previous) oldKeys.add(structure.key);
        }

        long now = System.currentTimeMillis();
        long cooldownMs = toastCooldownSeconds.get() * 1000L;

        for (PortalStructure structure : current) {
            if (oldKeys.contains(structure.key)) continue;

            long lastToast = lastToastTimes.getOrDefault(structure.key, 0L);
            if (now - lastToast < cooldownMs) continue;

            showDiscoveryToast(structure);
            lastToastTimes.put(structure.key, now);
        }
    }

    private void showDiscoveryToast(PortalStructure structure) {
        if (mc.getToastManager() == null) return;

        String label = structure.type == StructureType.Frame ? "Frame" : "Portal";
        Text title = Text.literal("NetherPortalESP");
        Text message = Text.literal(
            label + " detected at " + structure.anchor.getX() + ", " + structure.anchor.getY() + ", " + structure.anchor.getZ()
        );

        SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, title, message);
    }

    private void cleanupFarChunks() {
        if (mc.player == null) return;

        int keepRadius = mc.options.getViewDistance().getValue() + 6;
        ChunkPos playerChunk = mc.player.getChunkPos();

        Iterator<Map.Entry<Long, List<PortalStructure>>> mapIt = structuresByAnchorChunk.entrySet().iterator();
        while (mapIt.hasNext()) {
            Map.Entry<Long, List<PortalStructure>> entry = mapIt.next();
            int chunkX = ChunkPos.getPackedX(entry.getKey());
            int chunkZ = ChunkPos.getPackedZ(entry.getKey());

            if (Math.abs(chunkX - playerChunk.x) > keepRadius || Math.abs(chunkZ - playerChunk.z) > keepRadius) {
                mapIt.remove();
            }
        }

        Iterator<Long> queueIt = scanQueue.iterator();
        while (queueIt.hasNext()) {
            long key = queueIt.next();
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);

            if (Math.abs(chunkX - playerChunk.x) > keepRadius || Math.abs(chunkZ - playerChunk.z) > keepRadius) {
                queueIt.remove();
                queuedChunkKeys.remove(key);
            }
        }
    }

    private boolean matchesMode(StructureType type, DetectionMode mode) {
        return switch (mode) {
            case PortalOnly -> type == StructureType.Portal;
            case FrameOnly -> type == StructureType.Frame;
            case Both -> true;
        };
    }

    private boolean isEnclosedByAnyFrame(Box portalBox, List<PortalStructure> frames) {
        for (PortalStructure frame : frames) {
            Box frameBox = frame.box;
            if (portalBox.minX >= frameBox.minX && portalBox.maxX <= frameBox.maxX
                && portalBox.minY >= frameBox.minY && portalBox.maxY <= frameBox.maxY
                && portalBox.minZ >= frameBox.minZ && portalBox.maxZ <= frameBox.maxZ) {
                return true;
            }
        }

        return false;
    }

    private static double squaredDistance(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean affectsDetection(BlockState state) {
        return state != null && (state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.OBSIDIAN) || isValidInteriorBlock(state));
    }

    private boolean isObsidian(BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN);
    }

    private boolean isValidInteriorBlock(BlockState state) {
        return state.isAir() || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.NETHER_PORTAL);
    }

    private enum StructureType {
        Portal,
        Frame
    }

    public enum DetectionMode {
        PortalOnly,
        FrameOnly,
        Both
    }

    private static final class PortalBounds {
        private int minX;
        private int minY;
        private int minZ;
        private int maxX;
        private int maxY;
        private int maxZ;
        private boolean empty = true;

        private void include(BlockPos pos) {
            if (empty) {
                minX = maxX = pos.getX();
                minY = maxY = pos.getY();
                minZ = maxZ = pos.getZ();
                empty = false;
                return;
            }

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
    }

    private static final class PortalStructure {
        private final String key;
        private final StructureType type;
        private final Box box;
        private final Vec3d center;
        private final BlockPos anchor;

        private PortalStructure(String key, StructureType type, Box box, Vec3d center, BlockPos anchor) {
            this.key = key;
            this.type = type;
            this.box = box;
            this.center = center;
            this.anchor = anchor;
        }
    }
}
