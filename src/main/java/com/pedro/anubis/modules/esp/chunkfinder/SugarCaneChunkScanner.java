package com.pedro.anubis.modules.esp.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.IntSupplier;

public class SugarCaneChunkScanner implements ChunkScanner<SugarCaneChunkData> {
    private final World world;
    private final IntSupplier minHeight;

    public SugarCaneChunkScanner(World world, IntSupplier minHeight) {
        this.world = world;
        this.minHeight = minHeight;
    }

    @Override
    public SugarCaneChunkData scan(WorldChunk chunk) {
        ChunkPos cp = chunk.getPos();
        int xStart = cp.getStartX();
        int zStart = cp.getStartZ();
        int yMin = chunk.getBottomY();
        int yMaxExclusive = chunk.getBottomY() + chunk.getHeight();
        int requiredHeight = Math.max(1, minHeight.getAsInt());

        int qualifyingColumns = 0;
        int maxHeight = 0;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos.Mutable below = new BlockPos.Mutable();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMaxExclusive; y++) {
                    mutable.set(x, y, z);
                    if (!chunk.getBlockState(mutable).isOf(Blocks.SUGAR_CANE)) continue;

                    below.set(x, y - 1, z);
                    if (world.getBlockState(below).isOf(Blocks.SUGAR_CANE)) continue;

                    int height = 1;
                    cursor.set(x, y + 1, z);
                    while (cursor.getY() <= world.getTopYInclusive()) {
                        BlockState state = world.getBlockState(cursor);
                        if (!state.isOf(Blocks.SUGAR_CANE)) break;
                        height++;
                        cursor.set(x, cursor.getY() + 1, z);
                    }

                    if (height >= requiredHeight) {
                        qualifyingColumns++;
                        if (height > maxHeight) maxHeight = height;
                    }
                }
            }
        }

        return new SugarCaneChunkData(qualifyingColumns, maxHeight);
    }
}
