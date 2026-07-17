package rocks.minestom.worldgen.carver;

import rocks.minestom.worldgen.random.RandomSource;

/**
 * A carver paired with its parsed datapack configuration (vanilla
 * {@code ConfiguredWorldCarver}).
 */
public record ConfiguredCarver<C>(WorldCarver<C> carver, C config) {

    public boolean isStartChunk(RandomSource random) {
        return this.carver.isStartChunk(this.config, random);
    }

    public boolean carve(CarvingContext context, RandomSource random, int sourceChunkX, int sourceChunkZ) {
        return this.carver.carve(context, this.config, random, sourceChunkX, sourceChunkZ);
    }
}
