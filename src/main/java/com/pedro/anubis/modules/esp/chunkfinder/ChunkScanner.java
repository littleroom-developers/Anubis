package com.pedro.anubis.modules.esp.chunkfinder;

import net.minecraft.world.chunk.WorldChunk;

@FunctionalInterface
public interface ChunkScanner<T> {
    T scan(WorldChunk chunk);
}
