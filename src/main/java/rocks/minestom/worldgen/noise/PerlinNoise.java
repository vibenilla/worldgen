package rocks.minestom.worldgen.noise;

import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public final class PerlinNoise {
    private static final int ROUND_OFF = 33554432;

    private final ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final double[] amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    public static PerlinNoise createLegacyForBlendedNoise(RandomSource randomSource, IntStream octaves) {
        var octaveList = octaves.boxed().toList();
        var pair = makeAmplitudes(octaveList);
        return new PerlinNoise(randomSource, pair.firstOctave, pair.amplitudes, false);
    }

    public static PerlinNoise createLegacyForLegacyNetherBiome(RandomSource randomSource, int firstOctave, double[] amplitudes) {
        return new PerlinNoise(randomSource, firstOctave, amplitudes, false);
    }

    public static PerlinNoise create(RandomSource randomSource, int firstOctave, double[] amplitudes) {
        return new PerlinNoise(randomSource, firstOctave, amplitudes, true);
    }

    private PerlinNoise(RandomSource randomSource, int firstOctave, double[] amplitudes, boolean usePositionalFactory) {
        this.firstOctave = firstOctave;
        this.amplitudes = amplitudes;
        var octaveCount = amplitudes.length;
        var zeroIndex = -firstOctave;
        this.noiseLevels = new ImprovedNoise[octaveCount];

        if (usePositionalFactory) {
            var positionalRandomFactory = randomSource.forkPositional();
            for (var octaveIndex = 0; octaveIndex < octaveCount; octaveIndex++) {
                if (amplitudes[octaveIndex] != 0.0) {
                    var octave = firstOctave + octaveIndex;
                    this.noiseLevels[octaveIndex] = new ImprovedNoise(positionalRandomFactory.fromHashOf("octave_" + octave));
                }
            }
        } else {
            var sharedNoise = new ImprovedNoise(randomSource);
            if (zeroIndex >= 0 && zeroIndex < octaveCount) {
                var amplitude = amplitudes[zeroIndex];
                if (amplitude != 0.0) {
                    this.noiseLevels[zeroIndex] = sharedNoise;
                }
            }

            for (var octaveIndex = zeroIndex - 1; octaveIndex >= 0; octaveIndex--) {
                if (octaveIndex < octaveCount) {
                    var amplitude = amplitudes[octaveIndex];
                    if (amplitude != 0.0) {
                        this.noiseLevels[octaveIndex] = new ImprovedNoise(randomSource);
                    } else {
                        skipOctave(randomSource);
                    }
                } else {
                    skipOctave(randomSource);
                }
            }

            if (Arrays.stream(this.noiseLevels).filter(Objects::nonNull).count() != Arrays.stream(amplitudes).filter(amplitude -> amplitude != 0.0).count()) {
                throw new IllegalStateException("Failed to create correct number of noise levels for given non-zero amplitudes");
            }

            if (zeroIndex < octaveCount - 1) {
                throw new IllegalArgumentException("Positive octaves are temporarily disabled");
            }
        }

        this.lowestFreqInputFactor = Math.pow(2.0, (double) (-zeroIndex));
        this.lowestFreqValueFactor = Math.pow(2.0, (double) (octaveCount - 1)) / (Math.pow(2.0, (double) octaveCount) - 1.0);
        this.maxValue = this.edgeValue(2.0);
    }

    private static void skipOctave(RandomSource randomSource) {
        randomSource.consumeCount(262);
    }

    public double maxValue() {
        return this.maxValue;
    }

    public double maxBrokenValue(double value) {
        return this.edgeValue(value + 2.0);
    }

    private double edgeValue(double value) {
        var total = 0.0;
        var scale = this.lowestFreqValueFactor;

        for (var index = 0; index < this.noiseLevels.length; index++) {
            if (this.noiseLevels[index] != null) {
                total += this.amplitudes[index] * value * scale;
            }
            scale /= 2.0;
        }

        return total;
    }

    public ImprovedNoise getOctaveNoise(int octaveIndex) {
        return this.noiseLevels[this.noiseLevels.length - 1 - octaveIndex];
    }

    public double getValue(double x, double y, double z, double yScale, double yMax, boolean useWrappedY) {
        var total = 0.0;
        var inputFactor = this.lowestFreqInputFactor;
        var valueFactor = this.lowestFreqValueFactor;

        for (var index = 0; index < this.noiseLevels.length; index++) {
            var noise = this.noiseLevels[index];
            if (noise != null) {
                var sample = noise.noise(wrap(x * inputFactor), useWrappedY ? -noise.yo : wrap(y * inputFactor), wrap(z * inputFactor), yScale * inputFactor, yMax * inputFactor);
                total += this.amplitudes[index] * sample * valueFactor;
            }

            inputFactor *= 2.0;
            valueFactor /= 2.0;
        }

        return total;
    }

    public double getValue(double x, double y, double z) {
        return this.getValue(x, y, z, 0.0, 0.0, false);
    }

    public static double wrap(double value) {
        return value - (double) VMath.lfloor(value / (double) ROUND_OFF + 0.5) * (double) ROUND_OFF;
    }

    private record Octaves(int firstOctave, double[] amplitudes) {
    }

    private static Octaves makeAmplitudes(List<Integer> octaves) {
        if (octaves.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        }

        var min = -octaves.stream().min(Integer::compareTo).orElseThrow();
        var max = octaves.stream().max(Integer::compareTo).orElseThrow();
        var count = min + max + 1;
        if (count < 1) {
            throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
        }

        var amplitudes = new double[count];
        for (var octave : octaves) {
            amplitudes[octave + min] = 1.0;
        }
        return new Octaves(-min, amplitudes);
    }
}
