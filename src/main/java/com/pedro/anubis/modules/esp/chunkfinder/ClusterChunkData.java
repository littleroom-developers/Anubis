package com.pedro.anubis.modules.esp.chunkfinder;

public record ClusterChunkData(int clusterCount, int buddingCount, boolean hasSmoothBasalt, boolean hasCalcite) {
    public boolean hasGeode() {
        return buddingCount > 0 || hasSmoothBasalt || hasCalcite;
    }

    public boolean hasClusters() {
        return clusterCount > 0;
    }
}
