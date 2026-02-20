package com.pedro.anubis.modules.esp.chunkfinder;

public record KelpChunkData(int kelpColumns, int kelpTopsAt62) {
    public boolean hasSignal(int minColumns, int minTopsAt62, double minTop62Ratio) {
        boolean meetsCount = kelpColumns >= minColumns;
        boolean meetsTops = kelpTopsAt62 >= minTopsAt62;
        boolean meetsRatio = kelpColumns > 0 && ((double) kelpTopsAt62 / (double) kelpColumns) >= minTop62Ratio;
        return meetsCount && meetsTops && meetsRatio;
    }
}
