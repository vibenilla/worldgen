package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.density.DensityFunction;

import java.util.List;

/**
 * Biome source that selects biomes by comparing multi-noise climate samples to parameter ranges.
 * This partitions climate space into biome regions so large-scale temperature,
 * humidity, erosion, and continentalness patterns determine biome placement.
 */
public record MultiNoiseBiomeSource(ClimateSampler sampler, Climate.ParameterList<Key> parameters) implements BiomeSource {
    @Override
    public Key biome(int quartX, int quartY, int quartZ) {
        return this.parameters.findValue(this.sampler.sample(quartX, quartY, quartZ));
    }

    @Override
    public Key biome(int quartX, int quartY, int quartZ, DensityFunction.Context context) {
        return this.parameters.findValue(this.sampler.sample(context));
    }

    @Override
    public List<Key> possibleBiomes() {
        return this.parameters.values().stream().map(Pair::second).distinct().toList();
    }
}
