package com.pedro.anubis.modules.esp.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class ClusterChunkScanner implements ChunkScanner<ClusterChunkData> {
    private final int minYInclusive;
    private final int maxYInclusive;

    public ClusterChunkScanner(int minYInclusive, int maxYInclusive) {
        this.minYInclusive = minYInclusive;
        this.maxYInclusive = maxYInclusive;
    }

    @Override
    public ClusterChunkData scan(WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();

        int worldBottom = chunk.getBottomY();
        int worldTopInclusive = worldBottom + chunk.getHeight() - 1;

        int yMin = Math.max(worldBottom, minYInclusive);
        int yMax = Math.min(worldTopInclusive, maxYInclusive);
        if (yMin > yMax) return new ClusterChunkData(0, 0, false, false);

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        int buddingCount = 0;
        boolean hasSmoothBasalt = false;
        boolean hasCalcite = false;
        boolean geodeDetected = false;

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    mutable.set(x, y, z);
                    BlockState state = chunk.getBlockState(mutable);

                    if (state.isOf(Blocks.BUDDING_AMETHYST)) {
                        buddingCount++;
                        geodeDetected = true;
                    } else if (state.isOf(Blocks.SMOOTH_BASALT)) {
                        hasSmoothBasalt = true;
                        geodeDetected = true;
                    } else if (state.isOf(Blocks.CALCITE)) {
                        hasCalcite = true;
                        geodeDetected = true;
                    }
                }
            }
        }

        int clusterCount = 0;
        if (geodeDetected) {
            for (int x = startX; x < startX + 16; x++) {
                for (int z = startZ; z < startZ + 16; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        mutable.set(x, y, z);
                        if (chunk.getBlockState(mutable).isOf(Blocks.AMETHYST_CLUSTER)) clusterCount++;
                    }
                }
            }
        }

        return new ClusterChunkData(clusterCount, buddingCount, hasSmoothBasalt, hasCalcite);
    }
}
