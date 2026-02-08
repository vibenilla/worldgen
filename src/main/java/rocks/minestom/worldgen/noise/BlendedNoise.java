package rocks.minestom.worldgen.noise;

import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.stream.IntStream;

public final class BlendedNoise {
    private final PerlinNoise minLimitNoise;
    private final PerlinNoise maxLimitNoise;
    private final PerlinNoise mainNoise;
    private final double xzMultiplier;
    private final double yMultiplier;
    private final double xzFactor;
    private final double yFactor;
    private final double smearScaleMultiplier;
    private final double maxValue;
    private final double xzScale;
    private final double yScale;

    public BlendedNoise(RandomSource randomSource, double xzScale, double yScale, double xzFactor, double yFactor, double smearScaleMultiplier) {
        this(
                PerlinNoise.createLegacyForBlendedNoise(randomSource, IntStream.rangeClosed(-15, 0)),
                PerlinNoise.createLegacyForBlendedNoise(randomSource, IntStream.rangeClosed(-15, 0)),
                PerlinNoise.createLegacyForBlendedNoise(randomSource, IntStream.rangeClosed(-7, 0)),
                xzScale,
                yScale,
                xzFactor,
                yFactor,
                smearScaleMultiplier
        );
    }

    private BlendedNoise(
            PerlinNoise minLimitNoise,
            PerlinNoise maxLimitNoise,
            PerlinNoise mainNoise,
            double xzScale,
            double yScale,
            double xzFactor,
            double yFactor,
            double smearScaleMultiplier
    ) {
        this.minLimitNoise = minLimitNoise;
        this.maxLimitNoise = maxLimitNoise;
        this.mainNoise = mainNoise;
        this.xzScale = xzScale;
        this.yScale = yScale;
        this.xzFactor = xzFactor;
        this.yFactor = yFactor;
        this.smearScaleMultiplier = smearScaleMultiplier;
        this.xzMultiplier = 684.412 * this.xzScale;
        this.yMultiplier = 684.412 * this.yScale;
        this.maxValue = minLimitNoise.maxBrokenValue(this.yMultiplier);
    }

    public BlendedNoise withNewRandom(RandomSource randomSource) {
        return new BlendedNoise(randomSource, this.xzScale, this.yScale, this.xzFactor, this.yFactor, this.smearScaleMultiplier);
    }

    public double maxValue() {
        return this.maxValue;
    }

    public double compute(int blockX, int blockY, int blockZ) {
        var x = (double) blockX * this.xzMultiplier;
        var y = (double) blockY * this.yMultiplier;
        var z = (double) blockZ * this.xzMultiplier;

        var scaledX = x / this.xzFactor;
        var scaledY = y / this.yFactor;
        var scaledZ = z / this.xzFactor;

        var smearY = this.yMultiplier * this.smearScaleMultiplier;
        var scaledSmearY = smearY / this.yFactor;

        var mainTotal = 0.0;
        var mainScale = 1.0;

        for (var octaveIndex = 0; octaveIndex < 8; octaveIndex++) {
            var octave = this.mainNoise.getOctaveNoise(octaveIndex);

            if (octave != null) {
                mainTotal += octave.noise(
                        PerlinNoise.wrap(scaledX * mainScale),
                        PerlinNoise.wrap(scaledY * mainScale),
                        PerlinNoise.wrap(scaledZ * mainScale),
                        scaledSmearY * mainScale,
                        scaledY * mainScale
                ) / mainScale;
            }

            mainScale /= 2.0;
        }

        var interpolationValue = (mainTotal / 10.0 + 1.0) / 2.0;
        var skipMax = interpolationValue >= 1.0;
        var skipMin = interpolationValue <= 0.0;

        var minTotal = 0.0;
        var maxTotal = 0.0;
        var limitScale = 1.0;

        for (var octaveIndex = 0; octaveIndex < 16; octaveIndex++) {
            var wrapX = PerlinNoise.wrap(x * limitScale);
            var wrapY = PerlinNoise.wrap(y * limitScale);
            var wrapZ = PerlinNoise.wrap(z * limitScale);
            var yScaled = smearY * limitScale;

            if (!skipMax) {
                var octave = this.minLimitNoise.getOctaveNoise(octaveIndex);

                if (octave != null) {
                    minTotal += octave.noise(wrapX, wrapY, wrapZ, yScaled, y * limitScale) / limitScale;
                }
            }

            if (!skipMin) {
                var octave = this.maxLimitNoise.getOctaveNoise(octaveIndex);

                if (octave != null) {
                    maxTotal += octave.noise(wrapX, wrapY, wrapZ, yScaled, y * limitScale) / limitScale;
                }
            }

            limitScale /= 2.0;
        }

        return VMath.clampedLerp(interpolationValue, minTotal / 512.0, maxTotal / 512.0) / 128.0;
    }
}
