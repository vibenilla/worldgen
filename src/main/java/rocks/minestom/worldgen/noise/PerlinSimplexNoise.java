package rocks.minestom.worldgen.noise;

import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;
import java.util.TreeSet;

/**
 * Port of vanilla {@code PerlinSimplexNoise}: stacked two-dimensional simplex
 * octaves over a shared legacy random, used by the fixed-seed biome
 * temperature noises.
 */
public final class PerlinSimplexNoise {
    private final SimplexNoise[] noiseLevels;
    private final double highestFrequencyValueFactor;
    private final double highestFrequencyInputFactor;

    public PerlinSimplexNoise(RandomSource randomSource, List<Integer> octaves) {
        var octaveSet = new TreeSet<>(octaves);
        if (octaveSet.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        }

        var lowFrequencyOctaves = -octaveSet.first();
        var highFrequencyOctaves = octaveSet.last();
        var octaveCount = lowFrequencyOctaves + highFrequencyOctaves + 1;
        if (octaveCount < 1) {
            throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
        }

        var zeroOctave = new SimplexNoise(randomSource);
        var zeroOctaveIndex = highFrequencyOctaves;
        this.noiseLevels = new SimplexNoise[octaveCount];
        if (highFrequencyOctaves >= 0 && highFrequencyOctaves < octaveCount && octaveSet.contains(0)) {
            this.noiseLevels[highFrequencyOctaves] = zeroOctave;
        }

        for (var index = highFrequencyOctaves + 1; index < octaveCount; index++) {
            if (index >= 0 && octaveSet.contains(zeroOctaveIndex - index)) {
                this.noiseLevels[index] = new SimplexNoise(randomSource);
            } else {
                randomSource.consumeCount(262);
            }
        }

        if (highFrequencyOctaves > 0) {
            var positiveOctaveSeed = (long) (zeroOctave.getValue(zeroOctave.xo, zeroOctave.yo, zeroOctave.zo) * 9.223372E18F);
            var highFrequencyRandom = new LegacyRandomSource(positiveOctaveSeed);

            for (var index = zeroOctaveIndex - 1; index >= 0; index--) {
                if (index < octaveCount && octaveSet.contains(zeroOctaveIndex - index)) {
                    this.noiseLevels[index] = new SimplexNoise(highFrequencyRandom);
                } else {
                    highFrequencyRandom.consumeCount(262);
                }
            }
        }

        this.highestFrequencyInputFactor = Math.pow(2.0, highFrequencyOctaves);
        this.highestFrequencyValueFactor = 1.0 / (Math.pow(2.0, octaveCount) - 1.0);
    }

    public double getValue(double x, double y, boolean useNoiseOffsets) {
        var value = 0.0;
        var inputFactor = this.highestFrequencyInputFactor;
        var valueFactor = this.highestFrequencyValueFactor;

        for (var noiseLevel : this.noiseLevels) {
            if (noiseLevel != null) {
                value += noiseLevel.getValue(
                        x * inputFactor + (useNoiseOffsets ? noiseLevel.xo : 0.0),
                        y * inputFactor + (useNoiseOffsets ? noiseLevel.yo : 0.0)) * valueFactor;
            }

            inputFactor /= 2.0;
            valueFactor *= 2.0;
        }

        return value;
    }
}
