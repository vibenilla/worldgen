package rocks.minestom.worldgen.noise;

import rocks.minestom.worldgen.random.RandomSource;

public final class NormalNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227;

    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;
    private final NoiseParameters parameters;

    public static NormalNoise createLegacyNetherBiome(RandomSource randomSource, NoiseParameters noiseParameters) {
        return new NormalNoise(randomSource, noiseParameters, false);
    }

    public static NormalNoise create(RandomSource randomSource, NoiseParameters noiseParameters) {
        return new NormalNoise(randomSource, noiseParameters, true);
    }

    private NormalNoise(RandomSource randomSource, NoiseParameters noiseParameters, boolean usePositionalFactory) {
        var firstOctave = noiseParameters.firstOctave();
        var amplitudes = noiseParameters.amplitudes();
        this.parameters = noiseParameters;

        if (usePositionalFactory) {
            this.first = PerlinNoise.create(randomSource, firstOctave, amplitudes);
            this.second = PerlinNoise.create(randomSource, firstOctave, amplitudes);
        } else {
            this.first = PerlinNoise.createLegacyForLegacyNetherBiome(randomSource, firstOctave, amplitudes);
            this.second = PerlinNoise.createLegacyForLegacyNetherBiome(randomSource, firstOctave, amplitudes);
        }

        var minIndex = Integer.MAX_VALUE;
        var maxIndex = Integer.MIN_VALUE;
        for (var index = 0; index < amplitudes.length; index++) {
            var amplitude = amplitudes[index];
            if (amplitude != 0.0) {
                minIndex = Math.min(minIndex, index);
                maxIndex = Math.max(maxIndex, index);
            }
        }

        this.valueFactor = 0.16666666666666666 / expectedDeviation(maxIndex - minIndex);
        this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
    }

    private static double expectedDeviation(int octaves) {
        return 0.1 * (1.0 + 1.0 / (double) (octaves + 1));
    }

    public double maxValue() {
        return this.maxValue;
    }

    public double getValue(double x, double y, double z) {
        var scaledX = x * INPUT_FACTOR;
        var scaledY = y * INPUT_FACTOR;
        var scaledZ = z * INPUT_FACTOR;
        return (this.first.getValue(x, y, z) + this.second.getValue(scaledX, scaledY, scaledZ)) * this.valueFactor;
    }

    public record NoiseParameters(int firstOctave, double[] amplitudes) {
    }
}
