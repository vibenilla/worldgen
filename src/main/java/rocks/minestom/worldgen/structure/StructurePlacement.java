package rocks.minestom.worldgen.structure;

import rocks.minestom.worldgen.structure.placement.RandomSpreadPlacement;

/**
 * Determines where structures can start generating in the world.
 *
 * <p>Placement algorithms control the distribution pattern of structures across chunks.
 * They use the world seed to deterministically decide which chunks can contain
 * structure starts.
 *
 * @see RandomSpreadPlacement for grid-based random placement
 * @see StructureSet for combining structures with placement rules
 */
public interface StructurePlacement {

    /**
     * Checks if a structure can start generating in the given chunk.
     *
     * @param chunkX             the chunk X coordinate
     * @param chunkZ             the chunk Z coordinate
     * @param seed               the world seed
     * @param legacyRandomSource whether to use legacy random behavior
     * @return true if this chunk is valid for structure generation
     */
    boolean isStartChunk(int chunkX, int chunkZ, long seed, boolean legacyRandomSource);
}
