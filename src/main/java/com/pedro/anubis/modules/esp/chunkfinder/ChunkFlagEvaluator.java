package com.pedro.anubis.modules.esp.chunkfinder;

@FunctionalInterface
public interface ChunkFlagEvaluator<T> {
    boolean shouldFlag(T data);
}
