package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.density.ColumnCacheContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memoizes biome lookups per chunk column of quarts. The raw multi-noise source
 * re-walks six density-function trees per sample; terrain, surface rules, the
 * zoomer, and feature biome filters all hit the same quarts repeatedly, so one
 * cached pass per chunk mirrors vanilla reading biomes back from the chunk
 * palette instead of resampling climate.
 */
public final class CachedBiomeSource implements BiomeSource {
    private static final int MAX_CACHED_CHUNKS = 8192;

    private final BiomeSource source;
    private final int minQuartY;
    private final int quartHeight;
    private final Map<Long, Key[]> cache = new ConcurrentHashMap<>();

    public CachedBiomeSource(BiomeSource source, int minY, int height) {
        this.source = source;
        this.minQuartY = minY >> 2;
        this.quartHeight = height >> 2;
    }

    @Override
    public Key biome(int quartX, int quartY, int quartZ) {
        var yIndex = quartY - this.minQuartY;
        if (yIndex < 0 || yIndex >= this.quartHeight) {
            return this.source.biome(quartX, quartY, quartZ);
        }

        var column = this.chunkColumn(quartX >> 2, quartZ >> 2);
        return column[((quartX & 3) * 4 + (quartZ & 3)) * this.quartHeight + yIndex];
    }

    @Override
    public List<Key> possibleBiomes() {
        return this.source.possibleBiomes();
    }

    /**
     * All quart biomes of the chunk, laid out as (localQuartX * 4 + localQuartZ) * quartHeight + (quartY - minQuartY).
     */
    public Key[] chunkColumn(int chunkX, int chunkZ) {
        var key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
        var cached = this.cache.get(key);
        if (cached != null) {
            return cached;
        }

        var column = new Key[16 * this.quartHeight];
        var startQuartX = chunkX << 2;
        var startQuartZ = chunkZ << 2;

        // Vanilla fills one 4x4x4-quart chunk section at a time (sections bottom to
        // top), and within each section the nesting is x outer, y middle, z inner.
        // The climate R-tree keeps a last-result hint across calls, and distance
        // TIES resolve to whatever that hint was, so matching vanilla's exact
        // per-section x/y/z query order matches its tie outcomes too.
        var contexts = new ColumnCacheContext[16];
        for (var localX = 0; localX < 4; localX++) {
            for (var localZ = 0; localZ < 4; localZ++) {
                var context = new ColumnCacheContext();
                context.moveColumn((startQuartX + localX) << 2, (startQuartZ + localZ) << 2);
                contexts[localX * 4 + localZ] = context;
            }
        }

        for (var sectionStart = 0; sectionStart < this.quartHeight; sectionStart += 4) {
            for (var localX = 0; localX < 4; localX++) {
                for (var localY = 0; localY < 4; localY++) {
                    var yIndex = sectionStart + localY;
                    var quartY = this.minQuartY + yIndex;
                    for (var localZ = 0; localZ < 4; localZ++) {
                        var context = contexts[localX * 4 + localZ];
                        context.blockY(quartY << 2);
                        column[(localX * 4 + localZ) * this.quartHeight + yIndex] =
                                this.source.biome(startQuartX + localX, quartY, startQuartZ + localZ, context);
                    }
                }
            }
        }

        if (this.cache.size() > MAX_CACHED_CHUNKS) {
            this.cache.clear();
        }
        this.cache.put(key, column);
        return column;
    }
}
