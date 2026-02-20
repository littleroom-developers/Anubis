package com.pedro.anubis.modules.esp.chunkfinder;

public record DripstoneChunkData(
    int longStalactiteCount,
    int longStalagmiteCount,
    int maxStalactiteLength,
    int maxStalagmiteLength
) {
    public boolean hasStalactites() {
        return longStalactiteCount > 0;
    }

    public boolean hasStalagmites() {
        return longStalagmiteCount > 0;
    }

    public boolean isCombo() {
        return hasStalactites() && hasStalagmites();
    }
}
