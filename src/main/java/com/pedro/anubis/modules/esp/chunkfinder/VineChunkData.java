package com.pedro.anubis.modules.esp.chunkfinder;

public record VineChunkData(int groundedCount, int maxLength) {
    public boolean hasSignal(int threshold) {
        return groundedCount >= threshold;
    }
}
