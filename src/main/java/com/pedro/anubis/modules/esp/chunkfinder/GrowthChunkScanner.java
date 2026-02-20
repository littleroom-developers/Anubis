package com.pedro.anubis.modules.esp.chunkfinder;

import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.IntSupplier;

public class GrowthChunkScanner implements ChunkScanner<GrowthChunkData> {
    private final ClusterChunkScanner clusterScanner;
    private final DripstoneChunkScanner dripstoneScanner;
    private final KelpChunkScanner kelpScanner;
    private final CactusChunkScanner cactusScanner;
    private final SugarCaneChunkScanner sugarCaneScanner;
    private final BeehiveChunkScanner beehiveScanner;

    public GrowthChunkScanner(
            World world,
            int minYInclusive,
            int maxYInclusive,
            IntSupplier minStalactiteLength,
            IntSupplier minStalagmiteLength,
            IntSupplier minCactusHeight,
            IntSupplier minSugarCaneHeight) {
        this.clusterScanner = new ClusterChunkScanner(minYInclusive, maxYInclusive);
        this.dripstoneScanner = new DripstoneChunkScanner(world, minStalactiteLength, minStalagmiteLength);
        this.kelpScanner = new KelpChunkScanner(world);
        this.cactusScanner = new CactusChunkScanner(world, minCactusHeight);
        this.sugarCaneScanner = new SugarCaneChunkScanner(world, minSugarCaneHeight);
        this.beehiveScanner = new BeehiveChunkScanner(3);
    }

    @Override
    public GrowthChunkData scan(WorldChunk chunk) {
        ClusterChunkData cluster = clusterScanner.scan(chunk);
        DripstoneChunkData dripstone = dripstoneScanner.scan(chunk);
        KelpChunkData kelp = kelpScanner.scan(chunk);
        CactusChunkData cactus = cactusScanner.scan(chunk);
        SugarCaneChunkData sugarCane = sugarCaneScanner.scan(chunk);
        int beehiveHoney3Count = beehiveScanner.scan(chunk);
        return new GrowthChunkData(cluster, dripstone, kelp, cactus, sugarCane, beehiveHoney3Count);
    }
}
