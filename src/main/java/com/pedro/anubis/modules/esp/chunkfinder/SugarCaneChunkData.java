package com.pedro.anubis.modules.esp.chunkfinder;

public record SugarCaneChunkData(int qualifyingColumns, int maxHeight) {
    public boolean hasSignal(int minColumns) {
        return qualifyingColumns >= minColumns;
    }
}
