package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record ClampedNormalFloatProvider(float mean, float deviation, float min, float max) implements FloatProvider {
    public static final Codec<ClampedNormalFloatProvider> CODEC = StructCodec.struct(
            "mean", Codec.FLOAT, ClampedNormalFloatProvider::mean,
            "deviation", Codec.FLOAT, ClampedNormalFloatProvider::deviation,
            "min", Codec.FLOAT, ClampedNormalFloatProvider::min,
            "max", Codec.FLOAT, ClampedNormalFloatProvider::max,
            ClampedNormalFloatProvider::new);

    @Override
    public Key type() {
        return Key.key("minecraft:clamped_normal");
    }

    @Override
    public float sample(RandomSource random) {
        return sample(random, this.mean, this.deviation, this.min, this.max);
    }

    /**
     * Vanilla {@code ClampedNormalFloat.sample(random, mean, deviation, min, max)}.
     */
    public static float sample(RandomSource random, float mean, float deviation, float min, float max) {
        var value = mean + (float) GaussianSource.nextGaussian(random) * deviation;
        return Math.max(min, Math.min(max, value));
    }
}
