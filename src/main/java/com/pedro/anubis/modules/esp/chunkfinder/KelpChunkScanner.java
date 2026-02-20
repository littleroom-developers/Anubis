package com.pedro.anubis.modules.esp.chunkfinder;

import net.minecraft.block.Block;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.KelpPlantBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public class KelpChunkScanner implements ChunkScanner<KelpChunkData> {
    private static final int MIN_COLUMN_HEIGHT = 15;
    private final World world;

    public KelpChunkScanner(World world) {
        this.world = world;
    }

    @Override
    public KelpChunkData scan(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();

        int yMin = Math.max(world.getBottomY(), 30);
        int yMax = 63;

        int kelpColumns = 0;
        int kelpTopsAt62 = 0;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                int topY = -1;
                int bottomY = -1;

                for (int y = yMax; y >= yMin; y--) {
                    mutable.set(x, y, z);
                    Block block = chunk.getBlockState(mutable).getBlock();
                    boolean isKelp = block instanceof KelpBlock || block instanceof KelpPlantBlock;

                    if (isKelp) {
                        if (topY == -1) topY = y;
                        bottomY = y;
                        continue;
                    }

                    if (topY != -1) break;
                }

                if (topY == -1) continue;
                int height = topY - bottomY + 1;
                if (height < MIN_COLUMN_HEIGHT) continue;

                kelpColumns++;
                if (topY == 62) kelpTopsAt62++;
            }
        }

        return new KelpChunkData(kelpColumns, kelpTopsAt62);
    }
}
