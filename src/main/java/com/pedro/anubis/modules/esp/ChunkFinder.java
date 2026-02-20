package com.pedro.anubis.modules.esp;

import com.pedro.anubis.AnubisAddon;
import com.pedro.anubis.modules.esp.chunkfinder.ChunkFinderRuntime;
import com.pedro.anubis.modules.esp.chunkfinder.GrowthChunkData;
import com.pedro.anubis.modules.esp.chunkfinder.GrowthChunkScanner;
import com.pedro.anubis.modules.esp.chunkfinder.NotificationLimiter;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> foundColor = sgGeneral.add(new ColorSetting.Builder()
            .name("found-color")
            .description("The color of found chunks.")
            .defaultValue(new SettingColor(0, 255, 0, 100))
            .build());

    private final Setting<SettingColor> comboColor = sgGeneral.add(new ColorSetting.Builder()
            .name("combo-color")
            .description("The color of combo chunks.")
            .defaultValue(new SettingColor(255, 0, 0, 255, true))
            .build());

    private static final int THREAD_POOL_SIZE = 2;
    private static final long CLEANUP_INTERVAL_MS = 30_000L;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 128;

    // Hardcoded Settings
    private static final int CLUSTER_THRESHOLD = 5;
    private static final int STALACTITE_MIN_LENGTH = 7;
    private static final int STALAGMITE_MIN_LENGTH = 8;
    private static final int MIN_KELP_COLUMNS = 18;
    private static final int MIN_CACTUS_COLUMNS = 2;
    private static final int MIN_CACTUS_HEIGHT = 3;
    private static final int MIN_SUGAR_CANE_COLUMNS = 2;
    private static final int MIN_SUGAR_CANE_HEIGHT = 3;
    private static final int MIN_KELP_TOPS_AT_62 = 20;
    private static final double MIN_KELP_TOP_62_RATIO = 0.9;
    private static final int BEEHIVE_REQUIRED_HONEY3_IN_2X2 = 3;

    private static final int MAX_CHUNKS_RENDER = 80;
    private static final int COMBO_MIN_CHUNKS_IN_3X3 = 3;

    private static final boolean CHAT_NOTIFY = true;
    private static final boolean PLAY_SOUND = true;
    private static final int MAX_NOTIFICATIONS_PER_MINUTE = 5;
    private static final int NOTIFICATION_COOLDOWN_MS = 15_000;

    private ChunkFinderRuntime<GrowthChunkData> runtime;
    private NotificationLimiter<ChunkPos> notificationLimiter;

    public ChunkFinder() {
        super(AnubisAddon.ESP, "Chunk Finder", "Best chunk finder for Donut SMP");
    }

    @Override
    public void onActivate() {
        if (mc.world == null)
            return;

        GrowthChunkScanner scanner = new GrowthChunkScanner(
                mc.world,
                MIN_Y,
                MAX_Y,
                () -> STALACTITE_MIN_LENGTH,
                () -> STALAGMITE_MIN_LENGTH,
                () -> MIN_CACTUS_HEIGHT,
                () -> MIN_SUGAR_CANE_HEIGHT);

        notificationLimiter = new NotificationLimiter<>(MAX_NOTIFICATIONS_PER_MINUTE, NOTIFICATION_COOLDOWN_MS);

        runtime = new ChunkFinderRuntime<>(
                mc,
                scanner,
                this::shouldFlagChunk,
                this::notifyFound,
                THREAD_POOL_SIZE,
                1);

        runtime.activate();
        runtime.startInitialScan();
    }

    @Override
    public void onDeactivate() {
        if (runtime != null) {
            runtime.deactivate();
            runtime = null;
        }

        if (notificationLimiter != null) {
            notificationLimiter.clear();
            notificationLimiter = null;
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (runtime != null)
            runtime.onChunkLoad(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (runtime != null)
            runtime.onBlockUpdate(event.pos);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (runtime == null || mc.player == null)
            return;
        int cleanupBuffer = mc.options.getViewDistance().getValue() + 5;
        runtime.tickCleanup(System.currentTimeMillis(), CLEANUP_INTERVAL_MS, mc.player.getChunkPos(), cleanupBuffer);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (runtime == null || mc.player == null)
            return;

        Set<ChunkPos> comboCenters = computeComboCenters(runtime.flaggedChunks(), COMBO_MIN_CHUNKS_IN_3X3);
        int rendered = 0;
        for (ChunkPos cp : comboCenters) {
            if (rendered++ >= MAX_CHUNKS_RENDER)
                break;

            Color combo = comboColor.get();
            double x1 = cp.getStartX();
            double z1 = cp.getStartZ();
            Box box = new Box(x1, 64.0, z1, x1 + 16.0, 64.5, z1 + 16.0);
            event.renderer.box(box, combo, combo, ShapeMode.Both, 0);
        }

        for (ChunkPos cp : runtime.flaggedChunks()) {
            if (rendered++ >= MAX_CHUNKS_RENDER)
                break;
            if (comboCenters.contains(cp))
                continue;

            GrowthChunkData data = runtime.dataFor(cp);
            if (data == null)
                continue;

            double x1 = cp.getStartX();
            double z1 = cp.getStartZ();
            Box box = new Box(x1, 64.0, z1, x1 + 16.0, 64.5, z1 + 16.0);

            Color normal = foundColor.get();
            event.renderer.box(box, normal, normal, ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() {
        return runtime == null ? "0" : String.valueOf(runtime.flaggedCount());
    }

    private void notifyFound(ChunkPos chunkPos, GrowthChunkData data) {
        if (notificationLimiter == null)
            return;
        long now = System.currentTimeMillis();
        if (!notificationLimiter.shouldNotify(chunkPos, now))
            return;

        mc.execute(() -> {
            if (!isActive())
                return;

            if (CHAT_NOTIFY) {
                info("Chunk Found at [%d, %d]", chunkPos.x, chunkPos.z);
            }

            if (PLAY_SOUND) {
                mc.getSoundManager().play(
                        PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f));
            }
        });
    }

    private boolean shouldFlagChunk(ChunkPos chunkPos, GrowthChunkData data, Map<ChunkPos, GrowthChunkData> allData) {
        boolean clusterSignal = data.hasClusterSignal(CLUSTER_THRESHOLD);
        boolean dripstoneSignal = data.hasDripstoneSignal();
        boolean kelpSignal = data.hasKelpSignal(MIN_KELP_COLUMNS, MIN_KELP_TOPS_AT_62, MIN_KELP_TOP_62_RATIO);
        boolean cactusSignal = data.hasCactusSignal(MIN_CACTUS_COLUMNS);
        boolean sugarCaneSignal = data.hasSugarCaneSignal(MIN_SUGAR_CANE_COLUMNS);
        boolean beehiveSignal = hasBeehive2x2Signal(chunkPos, allData);
        return clusterSignal || dripstoneSignal || kelpSignal || cactusSignal || sugarCaneSignal || beehiveSignal;
    }

    private boolean hasBeehive2x2Signal(ChunkPos center) {
        return beehiveWindowCount(center) >= BEEHIVE_REQUIRED_HONEY3_IN_2X2;
    }

    private int beehiveWindowCount(ChunkPos center) {
        int max = 0;
        for (int baseX = center.x - 1; baseX <= center.x; baseX++) {
            for (int baseZ = center.z - 1; baseZ <= center.z; baseZ++) {
                int sum = beehiveCountAt(baseX, baseZ)
                        + beehiveCountAt(baseX + 1, baseZ)
                        + beehiveCountAt(baseX, baseZ + 1)
                        + beehiveCountAt(baseX + 1, baseZ + 1);
                if (sum > max)
                    max = sum;
            }
        }
        return max;
    }

    private int beehiveCountAt(int chunkX, int chunkZ) {
        if (runtime == null)
            return 0;
        GrowthChunkData data = runtime.dataFor(new ChunkPos(chunkX, chunkZ));
        if (data == null)
            return 0;
        return data.beehiveHoney3Count();
    }

    private boolean hasBeehive2x2Signal(ChunkPos center, Map<ChunkPos, GrowthChunkData> allData) {
        int required = BEEHIVE_REQUIRED_HONEY3_IN_2X2;
        for (int baseX = center.x - 1; baseX <= center.x; baseX++) {
            for (int baseZ = center.z - 1; baseZ <= center.z; baseZ++) {
                int sum = beehiveCountAt(baseX, baseZ, allData)
                        + beehiveCountAt(baseX + 1, baseZ, allData)
                        + beehiveCountAt(baseX, baseZ + 1, allData)
                        + beehiveCountAt(baseX + 1, baseZ + 1, allData);
                if (sum >= required)
                    return true;
            }
        }
        return false;
    }

    private int beehiveCountAt(int chunkX, int chunkZ, Map<ChunkPos, GrowthChunkData> allData) {
        GrowthChunkData data = allData.get(new ChunkPos(chunkX, chunkZ));
        if (data == null)
            return 0;
        return data.beehiveHoney3Count();
    }

    private Set<ChunkPos> computeComboCenters(Set<ChunkPos> flagged, int minCount) {
        Set<ChunkPos> centers = new HashSet<>();
        if (flagged.isEmpty())
            return centers;

        for (ChunkPos center : flagged) {
            int count = 0;
            for (ChunkPos other : flagged) {
                if (Math.abs(other.x - center.x) <= 1 && Math.abs(other.z - center.z) <= 1) {
                    count++;
                    if (count >= minCount) {
                        centers.add(center);
                        break;
                    }
                }
            }
        }

        return centers;
    }
}
