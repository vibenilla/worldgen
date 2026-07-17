package rocks.minestom.worldgen.structure.placement;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.biome.BiomeSource;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.StructurePlacement;
import rocks.minestom.worldgen.structure.context.BiomeTagManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of vanilla {@code ConcentricRingsStructurePlacement} plus the ring
 * position calculation from {@code ChunkGeneratorStructureState}: structures
 * are laid out on expanding rings around the origin, each candidate snapped
 * toward a preferred biome by a reservoir-sampled horizontal biome search.
 */
public final class ConcentricRingsPlacement implements StructurePlacement {
    private final int count;
    private final int distance;
    private final int spread;
    private final Key preferredBiomesTag;
    private final Map<Long, Set<Long>> positionsBySeed;

    public ConcentricRingsPlacement(int count, int distance, int spread, Key preferredBiomesTag) {
        this.count = count;
        this.distance = distance;
        this.spread = spread;
        this.preferredBiomesTag = preferredBiomesTag;
        this.positionsBySeed = new ConcurrentHashMap<>();
    }

    @Override
    public boolean isStartChunk(int chunkX, int chunkZ, long seed, boolean legacyRandomSource) {
        return false;
    }

    @Override
    public boolean isStartChunk(int chunkX, int chunkZ, long seed, boolean legacyRandomSource,
            BiomeSource biomeSource, BiomeTagManager biomeTags) {
        if (biomeSource == null || biomeTags == null) {
            return false;
        }

        var positions = this.positionsBySeed.computeIfAbsent(seed,
                ringSeed -> this.computePositions(ringSeed, biomeSource, biomeTags));
        return positions.contains(packChunk(chunkX, chunkZ));
    }

    private Set<Long> computePositions(long seed, BiomeSource biomeSource, BiomeTagManager biomeTags) {
        var positions = new HashSet<Long>();
        if (this.count == 0) {
            return positions;
        }

        var preferredBiomes = biomeTags.biomes(this.preferredBiomesTag);
        var random = new LegacyRandomSource(seed);
        var angle = random.nextDouble() * Math.PI * 2.0;
        var positionInCircle = 0;
        var circle = 0;
        var currentSpread = this.spread;

        for (var index = 0; index < this.count; index++) {
            var ringDistance = (double) (4 * this.distance + this.distance * circle * 6)
                    + (random.nextDouble() - 0.5) * this.distance * 2.5;
            var initialChunkX = (int) Math.round(Math.cos(angle) * ringDistance);
            var initialChunkZ = (int) Math.round(Math.sin(angle) * ringDistance);
            var biomeSearchRandom = new LegacyRandomSource(random.nextLong());

            var snapped = findBiomeHorizontal(biomeSource,
                    (initialChunkX << 4) + 8, 0, (initialChunkZ << 4) + 8, 112,
                    preferredBiomes, biomeSearchRandom);
            if (snapped != null) {
                positions.add(packChunk(snapped[0] >> 4, snapped[1] >> 4));
            } else {
                positions.add(packChunk(initialChunkX, initialChunkZ));
            }

            angle += (Math.PI * 2) / currentSpread;
            if (++positionInCircle == currentSpread) {
                circle++;
                positionInCircle = 0;
                currentSpread += 2 * currentSpread / (circle + 1);
                currentSpread = Math.min(currentSpread, this.count - index);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }

        return positions;
    }

    private static int[] findBiomeHorizontal(BiomeSource biomeSource, int originX, int originY, int originZ,
            int searchRadius, Set<Key> allowed, RandomSource random) {
        var centerQuartX = originX >> 2;
        var centerQuartZ = originZ >> 2;
        var quartRadius = searchRadius >> 2;
        var quartY = originY >> 2;
        int[] result = null;
        var found = 0;

        for (var offsetZ = -quartRadius; offsetZ <= quartRadius; offsetZ++) {
            for (var offsetX = -quartRadius; offsetX <= quartRadius; offsetX++) {
                var quartX = centerQuartX + offsetX;
                var quartZ = centerQuartZ + offsetZ;
                var biome = biomeSource.biome(quartX, quartY, quartZ);
                if (allowed.contains(biome)) {
                    if (result == null || random.nextInt(found + 1) == 0) {
                        result = new int[]{quartX << 2, quartZ << 2};
                    }
                    found++;
                }
            }
        }

        return result;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }
}
