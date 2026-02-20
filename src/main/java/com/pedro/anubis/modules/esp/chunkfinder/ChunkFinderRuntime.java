package com.pedro.anubis.modules.esp.chunkfinder;

import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class ChunkFinderRuntime<T> {
    private final MinecraftClient mc;
    private final ChunkScanner<T> scanner;
    private final ChunkFlagEvaluator<T> evaluator;
    private final ChunkFlagContextEvaluator<T> contextEvaluator;
    private final BiConsumer<ChunkPos, T> onFirstFlag;
    private final int threadPoolSize;
    private final int dependencyRadius;

    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, T> chunkData = new ConcurrentHashMap<>();

    private ExecutorService threadPool;
    private volatile boolean active;
    private long lastCleanupMs;

    public ChunkFinderRuntime(MinecraftClient mc, ChunkScanner<T> scanner, ChunkFlagEvaluator<T> evaluator, BiConsumer<ChunkPos, T> onFirstFlag, int threadPoolSize) {
        this.mc = mc;
        this.scanner = scanner;
        this.evaluator = evaluator;
        this.contextEvaluator = null;
        this.onFirstFlag = onFirstFlag;
        this.threadPoolSize = Math.max(1, threadPoolSize);
        this.dependencyRadius = 0;
    }

    public ChunkFinderRuntime(MinecraftClient mc, ChunkScanner<T> scanner, ChunkFlagContextEvaluator<T> contextEvaluator, BiConsumer<ChunkPos, T> onFirstFlag, int threadPoolSize, int dependencyRadius) {
        this.mc = mc;
        this.scanner = scanner;
        this.evaluator = null;
        this.contextEvaluator = contextEvaluator;
        this.onFirstFlag = onFirstFlag;
        this.threadPoolSize = Math.max(1, threadPoolSize);
        this.dependencyRadius = Math.max(0, dependencyRadius);
    }

    public void activate() {
        if (mc.world == null) return;

        active = true;
        lastCleanupMs = System.currentTimeMillis();

        flaggedChunks.clear();
        chunkData.clear();

        threadPool = Executors.newFixedThreadPool(threadPoolSize);
    }

    public void deactivate() {
        active = false;

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
            threadPool = null;
        }

        flaggedChunks.clear();
        chunkData.clear();
    }

    public boolean isActive() {
        return active;
    }

    public void startInitialScan() {
        try {
            for (var chunk : Utils.chunks()) {
                if (!active) return;
                if (!(chunk instanceof WorldChunk worldChunk)) continue;
                submitScan(worldChunk);
            }
        } catch (Throwable ignored) {
        }
    }

    public void onChunkLoad(WorldChunk chunk) {
        if (!active) return;
        submitScan(chunk);
    }

    public void onBlockUpdate(BlockPos pos) {
        if (!active || mc.world == null) return;

        ChunkPos chunkPos = new ChunkPos(pos);
        if (!chunkData.containsKey(chunkPos)) return;

        WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) return;
        submitScan(chunk);
    }

    public void tickCleanup(long nowMs, long cleanupIntervalMs, ChunkPos playerChunk, int chunkBuffer) {
        if (!active) return;
        if (nowMs - lastCleanupMs < cleanupIntervalMs) return;

        flaggedChunks.removeIf(cp -> Math.abs(cp.x - playerChunk.x) > chunkBuffer || Math.abs(cp.z - playerChunk.z) > chunkBuffer);
        chunkData.keySet().removeIf(cp -> Math.abs(cp.x - playerChunk.x) > chunkBuffer || Math.abs(cp.z - playerChunk.z) > chunkBuffer);
        lastCleanupMs = nowMs;
    }

    public int flaggedCount() {
        return flaggedChunks.size();
    }

    public Set<ChunkPos> flaggedChunks() {
        return flaggedChunks;
    }

    public T dataFor(ChunkPos chunkPos) {
        return chunkData.get(chunkPos);
    }

    private void submitScan(WorldChunk chunk) {
        ExecutorService pool = threadPool;
        if (pool != null && !pool.isShutdown()) pool.submit(() -> scanChunk(chunk));
        else scanChunk(chunk);
    }

    private void scanChunk(WorldChunk chunk) {
        if (!active) return;

        ChunkPos chunkPos = chunk.getPos();
        T data = scanner.scan(chunk);

        chunkData.put(chunkPos, data);
        reevaluateNeighborhood(chunkPos);
    }

    private void reevaluateNeighborhood(ChunkPos center) {
        int minX = center.x - dependencyRadius;
        int maxX = center.x + dependencyRadius;
        int minZ = center.z - dependencyRadius;
        int maxZ = center.z + dependencyRadius;

        for (Map.Entry<ChunkPos, T> entry : chunkData.entrySet()) {
            ChunkPos pos = entry.getKey();
            if (pos.x < minX || pos.x > maxX || pos.z < minZ || pos.z > maxZ) continue;
            evaluateSingle(pos, entry.getValue());
        }
    }

    private void evaluateSingle(ChunkPos chunkPos, T data) {
        boolean shouldFlag;
        if (contextEvaluator != null) shouldFlag = contextEvaluator.shouldFlag(chunkPos, data, chunkData);
        else shouldFlag = evaluator != null && evaluator.shouldFlag(data);

        if (shouldFlag) {
            if (flaggedChunks.add(chunkPos)) onFirstFlag.accept(chunkPos, data);
        } else {
            flaggedChunks.remove(chunkPos);
        }
    }
}
