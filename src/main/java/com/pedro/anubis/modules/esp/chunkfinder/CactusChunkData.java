package com.pedro.anubis.modules.esp.chunkfinder;

public record CactusChunkData(int qualifyingColumns, int maxHeight) {
    public boolean hasSignal(int minColumns) {
        return qualifyingColumns >= minColumns;
    }
}
