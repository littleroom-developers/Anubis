package com.pedro.anubis.modules.esp.chunkfinder;

import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class BeehiveChunkScanner implements ChunkScanner<Integer> {
    private final int targetHoneyLevel;

    public BeehiveChunkScanner(int targetHoneyLevel) {
        this.targetHoneyLevel = Math.max(0, Math.min(5, targetHoneyLevel));
    }

    @Override
    public Integer scan(WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int xStart = chunkPos.getStartX();
        int zStart = chunkPos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = chunk.getBottomY() + chunk.getHeight(); // exclusive

        int count = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    mutable.set(x, y, z);
                    BlockState state = chunk.getBlockState(mutable);

                    if (!(state.isOf(Blocks.BEEHIVE) || state.isOf(Blocks.BEE_NEST))) continue;
                    if (!state.contains(BeehiveBlock.HONEY_LEVEL)) continue;
                    if (state.get(BeehiveBlock.HONEY_LEVEL) >= targetHoneyLevel) count++;
                }
            }
        }

        return count;
    }
}
