package rocks.minestom.worldgen.structure.placement;

import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.structure.StructurePlacement;

/**
 * Places structures on a grid with random offsets within each cell.
 *
 * <p>The world is divided into regions of {@code spacing} chunks. Within each region,
 * exactly one structure can generate, offset randomly from the region corner by up to
 * {@code spacing - separation} chunks.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code spacing} - Region size in chunks (distance between potential spawns)
 *   <li>{@code separation} - Minimum distance between structures in chunks
 *   <li>{@code salt} - Unique value to differentiate structure types
 *   <li>{@code spreadType} - Distribution of random offset (linear or triangular)
 *   <li>{@code frequency} - Chance for a placement chunk to actually generate
 *   <li>{@code frequencyReduction} - Random scheme used for the frequency roll
 * </ul>
 *
 * @see RandomSpreadType for offset distribution options
 */
public record RandomSpreadPlacement(int spacing, int separation, int salt,
                                    RandomSpreadType spreadType, float frequency,
                                    FrequencyReduction frequencyReduction) implements StructurePlacement {

    @Override
    public boolean isStartChunk(int chunkX, int chunkZ, long seed, boolean legacyRandomSource) {
        var spacingValue = this.spacing;
        var separationValue = this.separation;
        if (spacingValue <= separationValue) {
            return false;
        }

        var regionX = Math.floorDiv(chunkX, spacingValue);
        var regionZ = Math.floorDiv(chunkZ, spacingValue);
        // Vanilla getPotentialStructureChunk always uses a legacy random
        // (setLargeFeatureWithSalt), regardless of the world's random type.
        var random = new LegacyRandomSource(0L);
        var regionSeed = (long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + (long) this.salt;
        random.setSeed(regionSeed);

        var offsetBound = spacingValue - separationValue;
        var offsetX = this.spreadType.sample(random, offsetBound);
        var offsetZ = this.spreadType.sample(random, offsetBound);
        var startChunkX = regionX * spacingValue + offsetX;
        var startChunkZ = regionZ * spacingValue + offsetZ;
        if (chunkX != startChunkX || chunkZ != startChunkZ) {
            return false;
        }

        // Vanilla applyAdditionalChunkRestrictions: frequency gate
        return !(this.frequency < 1.0F)
                || this.frequencyReduction.shouldGenerate(seed, this.salt, chunkX, chunkZ, this.frequency);
    }
}
