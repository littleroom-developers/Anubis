package com.pedro.anubis.modules.esp.chunkfinder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.IntSupplier;

public class DripstoneChunkScanner implements ChunkScanner<DripstoneChunkData> {
    private final World world;
    private final IntSupplier minStalactiteLength;
    private final IntSupplier minStalagmiteLength;

    public DripstoneChunkScanner(World world, IntSupplier minStalactiteLength, IntSupplier minStalagmiteLength) {
        this.world = world;
        this.minStalactiteLength = minStalactiteLength;
        this.minStalagmiteLength = minStalagmiteLength;
    }

    @Override
    public DripstoneChunkData scan(WorldChunk chunk) {
        ChunkPos cp = chunk.getPos();
        int xStart = cp.getStartX();
        int zStart = cp.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = chunk.getBottomY() + chunk.getHeight(); // exclusive

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos.Mutable above = new BlockPos.Mutable();
        BlockPos.Mutable below = new BlockPos.Mutable();

        int longStalactiteCount = 0;
        int longStalagmiteCount = 0;
        int maxStalactiteLength = 0;
        int maxStalagmiteLength = 0;
        int minStalactite = Math.max(2, minStalactiteLength.getAsInt());
        int minStalagmite = Math.max(2, minStalagmiteLength.getAsInt());

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    mutable.set(x, y, z);
                    BlockState state = chunk.getBlockState(mutable);

                    if (isPointedDown(state)) {
                        above.set(x, y + 1, z);
                        if (!isPointedDown(world.getBlockState(above))) {
                            int length = measureDownLength(mutable);
                            if (length >= minStalactite) {
                                longStalactiteCount++;
                                if (length > maxStalactiteLength) maxStalactiteLength = length;
                            }
                        }
                    }

                    if (isPointedUp(state)) {
                        below.set(x, y - 1, z);
                        if (!isPointedUp(world.getBlockState(below))) {
                            int length = measureUpLength(mutable);
                            if (length >= minStalagmite) {
                                longStalagmiteCount++;
                                if (length > maxStalagmiteLength) maxStalagmiteLength = length;
                            }
                        }
                    }
                }
            }
        }

        return new DripstoneChunkData(longStalactiteCount, longStalagmiteCount, maxStalactiteLength, maxStalagmiteLength);
    }

    private int measureDownLength(BlockPos start) {
        int length = 0;
        BlockPos.Mutable cursor = start.mutableCopy();
        int bottom = world.getBottomY();

        while (cursor.getY() >= bottom) {
            if (!isPointedDown(world.getBlockState(cursor))) break;
            length++;
            cursor.move(Direction.DOWN);
        }

        return length;
    }

    private int measureUpLength(BlockPos start) {
        int length = 0;
        BlockPos.Mutable cursor = start.mutableCopy();
        int topExclusive = world.getTopYInclusive() + 1;

        while (cursor.getY() < topExclusive) {
            if (!isPointedUp(world.getBlockState(cursor))) break;
            length++;
            cursor.move(Direction.UP);
        }

        return length;
    }

    private static boolean isPointedDown(BlockState state) {
        if (!state.isOf(Blocks.POINTED_DRIPSTONE)) return false;
        if (!state.contains(PointedDripstoneBlock.VERTICAL_DIRECTION)) return false;
        return state.get(PointedDripstoneBlock.VERTICAL_DIRECTION) == Direction.DOWN;
    }

    private static boolean isPointedUp(BlockState state) {
        if (!state.isOf(Blocks.POINTED_DRIPSTONE)) return false;
        if (!state.contains(PointedDripstoneBlock.VERTICAL_DIRECTION)) return false;
        return state.get(PointedDripstoneBlock.VERTICAL_DIRECTION) == Direction.UP;
    }
}
