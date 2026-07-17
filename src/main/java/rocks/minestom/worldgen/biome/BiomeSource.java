package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.density.DensityFunction;

import java.util.List;

/**
 * Chooses a biome for a coordinate based on climate sampling inputs.
 * This is the source of biome identity for terrain and features, translating
 * climate signals into concrete biome selections across the world.
 */
public interface BiomeSource {
    Key biome(int quartX, int quartY, int quartZ);

    /**
     * Samples with a caller-managed density context so chunk fills can reuse
     * column-cached climate evaluation. Defaults to the plain lookup.
     */
    default Key biome(int quartX, int quartY, int quartZ, DensityFunction.Context context) {
        return this.biome(quartX, quartY, quartZ);
    }

    /**
     * All biomes this source can produce, in vanilla registration order.
     * The order seeds the deterministic feature ordering, so it must match vanilla.
     */
    List<Key> possibleBiomes();
}
