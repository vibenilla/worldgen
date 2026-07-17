package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.noise.PerlinSimplexNoise;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.surface.BiomeResolver;

import java.util.List;

/**
 * Port of vanilla {@code Biome}'s climate queries: the fixed-seed temperature
 * noises, the frozen temperature modifier, and the height-adjusted temperature
 * behind {@code coldEnoughToSnow} and {@code warmEnoughToRain}.
 */
public final class BiomeClimate {
    private static final PerlinSimplexNoise TEMPERATURE_NOISE = new PerlinSimplexNoise(new LegacyRandomSource(1234L), List.of(0));
    private static final PerlinSimplexNoise FROZEN_TEMPERATURE_NOISE = new PerlinSimplexNoise(new LegacyRandomSource(3456L), List.of(-2, -1, 0));
    private static final PerlinSimplexNoise BIOME_INFO_NOISE = new PerlinSimplexNoise(new LegacyRandomSource(2345L), List.of(0));

    private BiomeClimate() {
    }

    public static boolean coldEnoughToSnow(BiomeResolver resolver, Key biome, int x, int y, int z, int seaLevel) {
        return !warmEnoughToRain(resolver, biome, x, y, z, seaLevel);
    }

    public static boolean warmEnoughToRain(BiomeResolver resolver, Key biome, int x, int y, int z, int seaLevel) {
        return heightAdjustedTemperature(resolver, biome, x, y, z, seaLevel) >= 0.15F;
    }

    public static float heightAdjustedTemperature(BiomeResolver resolver, Key biome, int x, int y, int z, int seaLevel) {
        var baseTemperature = resolver.temperature(biome);
        var adjustedTemperature = resolver.frozenTemperatureModifier(biome)
                ? frozenTemperature(x, z, baseTemperature)
                : baseTemperature;
        var snowLevel = seaLevel + 17;
        if (y > snowLevel) {
            var noise = (float) (TEMPERATURE_NOISE.getValue((float) x / 8.0F, (float) z / 8.0F, false) * 8.0);
            return adjustedTemperature - (noise + (float) y - (float) snowLevel) * 0.05F / 40.0F;
        }
        return adjustedTemperature;
    }

    private static float frozenTemperature(int x, int z, float baseTemperature) {
        var groundValueLargeVariation = FROZEN_TEMPERATURE_NOISE.getValue(x * 0.05, z * 0.05, false) * 7.0;
        var groundValueEdgeVariation = BIOME_INFO_NOISE.getValue(x * 0.2, z * 0.2, false);
        var icePatches = groundValueLargeVariation + groundValueEdgeVariation;
        if (icePatches < 0.3) {
            var groundValueSmallVariation = BIOME_INFO_NOISE.getValue(x * 0.09, z * 0.09, false);
            if (groundValueSmallVariation < 0.8) {
                return 0.2F;
            }
        }
        return baseTemperature;
    }
}
