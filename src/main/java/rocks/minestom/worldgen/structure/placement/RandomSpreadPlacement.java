package rocks.minestom.worldgen.structure.placement;

import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;
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
 * </ul>
 *
 * @see RandomSpreadType for offset distribution options
 */
public final class RandomSpreadPlacement implements StructurePlacement {
    private final int spacing;
    private final int separation;
    private final int salt;
    private final RandomSpreadType spreadType;

    public RandomSpreadPlacement(int spacing, int separation, int salt, RandomSpreadType spreadType) {
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.spreadType = spreadType;
    }

    public int spacing() {
        return this.spacing;
    }

    public int separation() {
        return this.separation;
    }

    public int salt() {
        return this.salt;
    }

    public RandomSpreadType spreadType() {
        return this.spreadType;
    }

    @Override
    public boolean isStartChunk(int chunkX, int chunkZ, long seed, boolean legacyRandomSource) {
        var spacingValue = this.spacing;
        var separationValue = this.separation;
        if (spacingValue <= separationValue) {
            return false;
        }

        var regionX = Math.floorDiv(chunkX, spacingValue);
        var regionZ = Math.floorDiv(chunkZ, spacingValue);
        var random = createRandomSource(legacyRandomSource);
        var regionSeed = (long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + (long) this.salt;
        random.setSeed(regionSeed);

        var offsetBound = spacingValue - separationValue;
        var offsetX = this.spreadType.sample(random, offsetBound);
        var offsetZ = this.spreadType.sample(random, offsetBound);
        var startChunkX = regionX * spacingValue + offsetX;
        var startChunkZ = regionZ * spacingValue + offsetZ;
        return chunkX == startChunkX && chunkZ == startChunkZ;
    }

    private static RandomSource createRandomSource(boolean legacyRandomSource) {
        if (legacyRandomSource) {
            return new LegacyRandomSource(0L);
        }
        return new XoroshiroRandomSource(0L);
    }
}
