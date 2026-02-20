package com.pedro.anubis.modules.esp.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.IntSupplier;

public class VineChunkScanner implements ChunkScanner<VineChunkData> {
    private final World world;
    private final IntSupplier minLength;
    private final IntSupplier minY;
    private final IntSupplier maxY;

    public VineChunkScanner(World world, IntSupplier minLength, IntSupplier minY, IntSupplier maxY) {
        this.world = world;
        this.minLength = minLength;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public VineChunkData scan(WorldChunk chunk) {
        ChunkPos cp = chunk.getPos();
        int xStart = cp.getStartX();
        int zStart = cp.getStartZ();

        int yMin = Math.max(chunk.getBottomY(), Math.max(world.getBottomY(), minY.getAsInt()));
        int yMaxExclusive = Math.min(
            chunk.getBottomY() + chunk.getHeight(),
            Math.min(world.getTopYInclusive() + 1, maxY.getAsInt() + 1)
        );

        if (yMin >= yMaxExclusive) return new VineChunkData(0, 0);

        int requiredLength = Math.max(1, minLength.getAsInt());
        int groundedCount = 0;
        int maxLength = 0;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos.Mutable below = new BlockPos.Mutable();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMaxExclusive; y++) {
                    mutable.set(x, y, z);
                    BlockState state = chunk.getBlockState(mutable);
                    if (!state.isOf(Blocks.VINE)) continue;

                    below.set(x, y - 1, z);
                    BlockState belowState = world.getBlockState(below);
                    if (belowState.isOf(Blocks.VINE)) continue;
                    if (belowState.isAir()) continue;
                    if (!belowState.isSolidBlock(world, below)) continue;

                    int length = 1;
                    cursor.set(x, y + 1, z);
                    while (cursor.getY() <= world.getTopYInclusive() && world.getBlockState(cursor).isOf(Blocks.VINE)) {
                        length++;
                        cursor.set(cursor.getX(), cursor.getY() + 1, cursor.getZ());
                    }

                    if (length >= requiredLength) {
                        groundedCount++;
                        if (length > maxLength) maxLength = length;
                    }
                }
            }
        }

        return new VineChunkData(groundedCount, maxLength);
    }
}
