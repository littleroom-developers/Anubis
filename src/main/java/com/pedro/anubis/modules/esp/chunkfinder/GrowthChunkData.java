package com.pedro.anubis.modules.esp.chunkfinder;

public record GrowthChunkData(
        ClusterChunkData cluster,
        DripstoneChunkData dripstone,
        KelpChunkData kelp,
        CactusChunkData cactus,
        SugarCaneChunkData sugarCane,
        int beehiveHoney3Count) {
    public boolean hasClusterSignal(int clusterThreshold) {
        return cluster != null && cluster.hasClusters() && cluster.clusterCount() >= clusterThreshold;
    }

    public boolean hasDripstoneSignal() {
        return dripstone != null && (dripstone.hasStalactites() || dripstone.hasStalagmites());
    }

    public boolean hasKelpSignal(int minColumns, int minTopsAt62, double minTop62Ratio) {
        return kelp != null && kelp.hasSignal(minColumns, minTopsAt62, minTop62Ratio);
    }

    public boolean hasCactusSignal(int minColumns) {
        return cactus != null && cactus.hasSignal(minColumns);
    }

    public boolean hasSugarCaneSignal(int minColumns) {
        return sugarCane != null && sugarCane.hasSignal(minColumns);
    }

    public boolean hasBeehiveSignal() {
        return beehiveHoney3Count > 0;
    }

    public boolean hasDripstoneStalactiteOnly() {
        return dripstone != null && dripstone.hasStalactites() && !dripstone.hasStalagmites();
    }

    public boolean hasDripstoneStalagmiteOnly() {
        return dripstone != null && !dripstone.hasStalactites() && dripstone.hasStalagmites();
    }

    public boolean hasDripstoneBoth() {
        return dripstone != null && dripstone.hasStalactites() && dripstone.hasStalagmites();
    }

    public int signalCount(
            int clusterThreshold,
            boolean kelpSignal,
            boolean cactusSignal,
            boolean sugarCaneSignal,
            boolean beehiveSignal) {
        int signals = 0;
        if (hasClusterSignal(clusterThreshold))
            signals++;
        if (hasDripstoneSignal())
            signals++;
        if (kelpSignal)
            signals++;
        if (cactusSignal)
            signals++;
        if (sugarCaneSignal)
            signals++;
        if (beehiveSignal)
            signals++;
        return signals;
    }
}
