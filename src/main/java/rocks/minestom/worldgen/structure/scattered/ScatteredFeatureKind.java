package rocks.minestom.worldgen.structure.scattered;

/**
 * Which vanilla single-piece scattered feature a {@link ScatteredFeatureStructure}
 * generates: {@code minecraft:desert_pyramid}, {@code minecraft:jungle_temple}
 * or {@code minecraft:swamp_hut}.
 */
public enum ScatteredFeatureKind {
    DESERT_PYRAMID,
    JUNGLE_TEMPLE,
    SWAMP_HUT;

    /**
     * Whether vanilla's {@code SinglePieceStructure.findGenerationPoint} gates
     * this kind's start behind a {@code getLowestY(width, depth) >= seaLevel}
     * check across its footprint before ever sampling a biome (the swamp hut
     * is a plain {@code Structure}, not a {@code SinglePieceStructure}, so it
     * has no such gate).
     */
    public boolean hasSeaLevelGate() {
        return this == DESERT_PYRAMID || this == JUNGLE_TEMPLE;
    }

    /** The footprint width used by the sea level gate, matching the piece's own constructor size. */
    public int footprintWidth() {
        return switch (this) {
            case DESERT_PYRAMID -> 21;
            case JUNGLE_TEMPLE -> 12;
            case SWAMP_HUT -> 0;
        };
    }

    /** The footprint depth used by the sea level gate, matching the piece's own constructor size. */
    public int footprintDepth() {
        return switch (this) {
            case DESERT_PYRAMID -> 21;
            case JUNGLE_TEMPLE -> 15;
            case SWAMP_HUT -> 0;
        };
    }
}
